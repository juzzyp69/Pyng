// Package core is the messaging engine behind Pyng. It links to Google Messages
// the same way Messages for Web does, and exposes a small API that the Android
// app calls through gomobile. Everything crossing the boundary is a string,
// number, bool or JSON, so the generated Java/Kotlin bindings stay simple.
package core

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"runtime/debug"
	"sync"
	"time"

	"github.com/rs/zerolog"
	"go.mau.fi/util/exhttp"

	"go.mau.fi/mautrix-gmessages/pkg/libgm"
	"go.mau.fi/mautrix-gmessages/pkg/libgm/events"
	"go.mau.fi/mautrix-gmessages/pkg/libgm/gmproto"
)

// Listener receives everything that happens asynchronously. kind is a short
// event name and payload is a JSON object (or an empty object).
type Listener interface {
	OnEvent(kind string, payload string)
}

// Messenger owns one paired Google Messages session.
type Messenger struct {
	dir      string
	listener Listener
	log      zerolog.Logger
	logFile  *os.File

	mu        sync.Mutex
	connectMu sync.Mutex
	auth      *libgm.AuthData
	client    *libgm.Client
	convs     map[string]*gmproto.Conversation
	sims      []*gmproto.SIMCard
	pairing   context.CancelFunc
}

// NewMessenger creates the engine. dataDir is a private app folder where the
// session and a rolling debug log are kept.
func NewMessenger(dataDir string, listener Listener) *Messenger {
	_ = os.MkdirAll(dataDir, 0o700)
	m := &Messenger{dir: dataDir, listener: listener, convs: map[string]*gmproto.Conversation{}}
	inlineDir = m.cacheDir("media")
	logPath := filepath.Join(dataDir, "engine.log")
	if st, err := os.Stat(logPath); err == nil && st.Size() > 2<<20 {
		_ = os.Rename(logPath, logPath+".old")
	}
	if f, err := os.OpenFile(logPath, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o600); err == nil {
		m.logFile = f
		m.log = zerolog.New(f).With().Timestamp().Logger().Level(zerolog.DebugLevel)
	} else {
		m.log = zerolog.Nop()
	}
	// Fatal Go crashes (which would otherwise only reach logcat) are written to
	// crash.txt. A crash from the previous run is kept as crash-last.txt.
	crashPath := filepath.Join(dataDir, "crash.txt")
	if st, err := os.Stat(crashPath); err == nil && st.Size() > 0 {
		_ = os.Rename(crashPath, filepath.Join(dataDir, "crash-last.txt"))
	}
	if f, err := os.OpenFile(crashPath, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0o600); err == nil {
		_ = debug.SetCrashOutput(f, debug.CrashOptions{})
		_ = f.Close()
	}
	m.auth = m.loadSession()
	m.log.Info().Bool("paired", m.auth != nil).Msg("Engine started")
	return m
}

// LastCrash returns the report of a crash from the previous run, if any.
func (m *Messenger) LastCrash() string {
	data, err := os.ReadFile(filepath.Join(m.dir, "crash-last.txt"))
	if err != nil {
		return ""
	}
	return string(data)
}

// ClearCrash forgets the previous crash report.
func (m *Messenger) ClearCrash() {
	_ = os.Remove(filepath.Join(m.dir, "crash-last.txt"))
}

// guard turns a panic inside an exported call into an error instead of a crash.
func (m *Messenger) guard(name string, err *error) {
	if r := recover(); r != nil {
		m.log.Error().Any("panic", r).Str("call", name).Bytes("stack", debug.Stack()).Msg("Recovered panic")
		if err != nil {
			*err = fmt.Errorf("internal error in %s: %v", name, r)
		}
	}
}

// ---------- session storage ----------

func (m *Messenger) sessionPath() string { return filepath.Join(m.dir, "session.json") }

func (m *Messenger) loadSession() *libgm.AuthData {
	data, err := os.ReadFile(m.sessionPath())
	if err != nil {
		return nil
	}
	ad := libgm.NewAuthData()
	if err = json.Unmarshal(data, ad); err != nil {
		m.log.Err(err).Msg("Saved session is unreadable")
		return nil
	}
	return ad
}

func (m *Messenger) saveSession() {
	m.mu.Lock()
	ad := m.auth
	m.mu.Unlock()
	if ad == nil {
		return
	}
	data, err := json.Marshal(ad)
	if err != nil {
		m.log.Err(err).Msg("Failed to encode session")
		return
	}
	tmp := m.sessionPath() + ".tmp"
	if err = os.WriteFile(tmp, data, 0o600); err == nil {
		err = os.Rename(tmp, m.sessionPath())
	}
	if err != nil {
		m.log.Err(err).Msg("Failed to save session")
	}
}

// IsPaired reports whether a usable session is stored on this device.
func (m *Messenger) IsPaired() bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.auth != nil && m.auth.Mobile != nil && len(m.auth.TachyonAuthToken) > 0
}

// IsConnected reports whether the live connection is up.
func (m *Messenger) IsConnected() bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.client != nil && m.client.IsConnected()
}

// ---------- client lifecycle ----------

func (m *Messenger) newClient(ad *libgm.AuthData) *libgm.Client {
	cli := libgm.NewClient(ad, nil, m.log.With().Str("component", "libgm").Logger(), exhttp.SensibleClientSettings)
	cli.SetEventHandler(m.handleEvent)
	return cli
}

// Start connects using the stored session. Safe to call repeatedly.
func (m *Messenger) Start() (err error) {
	defer m.guard("Start", &err)
	m.connectMu.Lock()
	defer m.connectMu.Unlock()
	if !m.IsPaired() {
		return errors.New("not paired")
	}
	m.mu.Lock()
	if m.client == nil {
		m.client = m.newClient(m.auth)
	}
	cli := m.client
	m.mu.Unlock()
	if cli.IsConnected() {
		return nil
	}
	m.emit("connection", map[string]any{"state": "connecting"})
	m.log.Info().Msg("Connecting")
	if err := cli.Connect(context.Background()); err != nil {
		m.log.Err(err).Msg("Connect failed")
		m.emit("connection", map[string]any{"state": "error", "error": err.Error()})
		return err
	}
	m.saveSession()
	return nil
}

// Stop drops the live connection but keeps the pairing.
func (m *Messenger) Stop() {
	m.mu.Lock()
	cli := m.client
	m.mu.Unlock()
	if cli != nil {
		cli.Disconnect()
	}
}

// ---------- pairing ----------

// StartGooglePairing begins pairing with a Google account. cookiesJSON is a
// JSON object of cookie name to value taken from a signed-in session at
// messages.google.com. It returns the emoji the person must tap in Google
// Messages; the result arrives later as a "paired" or "pair_error" event.
func (m *Messenger) StartGooglePairing(cookiesJSON string) (emoji string, err error) {
	defer m.guard("StartGooglePairing", &err)
	cookies := map[string]string{}
	if err = json.Unmarshal([]byte(cookiesJSON), &cookies); err != nil {
		return "", fmt.Errorf("bad cookies: %w", err)
	}
	for _, k := range []string{"SID", "HSID", "SSID", "APISID", "SAPISID", "OSID"} {
		if cookies[k] == "" {
			return "", fmt.Errorf("missing sign-in cookie %s", k)
		}
	}
	m.cancelPairing()
	m.log.Info().Int("cookies", len(cookies)).Msg("Starting Google account pairing")
	ad := libgm.NewAuthData()
	ad.SetCookies(cookies)
	var (
		cli      *libgm.Client
		sess     *libgm.PairingSession
		bgCtx    context.Context
		bgCancel context.CancelFunc
	)
	// An account that has used Google Messages on more than one phone lists all of them. If the
	// newest one doesn't answer, try the next, the way the Google Messages bridge lets you retry.
	for attempt := 0; attempt < 3; attempt++ {
		cli = m.newClient(ad)
		cli.GaiaHackyDeviceSwitcher = attempt
		ctx, cancel := context.WithTimeout(context.Background(), 35*time.Second)
		if err = cli.FetchConfig(ctx); err != nil {
			cancel()
			m.log.Err(err).Msg("FetchConfig failed")
			return "", fmt.Errorf("could not reach Google Messages: %w", err)
		}
		bgCtx, bgCancel = context.WithCancel(context.Background())
		emoji, sess, err = cli.StartGaiaPairing(ctx, bgCtx)
		cancel()
		if err == nil {
			break
		}
		m.log.Err(err).Int("attempt", attempt).Msg("StartGaiaPairing failed")
		bgCancel()
		cli.Disconnect()
		if !errors.Is(err, libgm.ErrHadMultipleDevices) || attempt == 2 {
			return "", friendlyPairError(err)
		}
		m.log.Info().Msg("Trying the next phone listed on this Google account")
	}
	m.mu.Lock()
	m.pairing = bgCancel
	m.mu.Unlock()

	m.log.Info().Str("emoji", emoji).Msg("Waiting for emoji confirmation")
	go func() {
		defer m.guard("FinishGaiaPairing", nil)
		phoneID, err := cli.FinishGaiaPairing(bgCtx, sess)
		bgCancel()
		if err != nil {
			m.log.Err(err).Msg("FinishGaiaPairing failed")
			cli.Disconnect()
			m.emit("pair_error", map[string]any{"error": friendlyPairError(err).Error()})
			return
		}
		m.log.Info().Str("phone", phoneID).Msg("Paired")
		// Reconnect with the new keys before anything else uses the client.
		m.connectMu.Lock()
		m.mu.Lock()
		old := m.client
		m.client = cli
		m.auth = ad
		m.mu.Unlock()
		if old != nil && old != cli {
			old.Disconnect()
		}
		m.saveSession()
		if err := cli.Reconnect(context.Background()); err != nil {
			m.log.Err(err).Msg("Reconnect after pairing failed")
		}
		m.connectMu.Unlock()
		m.emit("paired", map[string]any{"phone": phoneID})
	}()
	return emoji, nil
}

// StartQRPairing returns a URL to show as a QR code. Google Messages on a phone
// scans it from Settings > Device pairing. Success arrives as a "paired" event.
func (m *Messenger) StartQRPairing() (string, error) {
	m.cancelPairing()
	ad := libgm.NewAuthData()
	cli := m.newClient(ad)
	ctx, cancel := context.WithCancel(context.Background())
	m.mu.Lock()
	m.pairing = cancel
	m.client = cli
	m.auth = ad
	m.mu.Unlock()
	qr, err := cli.StartLogin(ctx)
	if err != nil {
		cancel()
		return "", fmt.Errorf("could not start QR pairing: %w", err)
	}
	return qr, nil
}

// CancelPairing stops any pairing attempt in progress.
func (m *Messenger) CancelPairing() { m.cancelPairing() }

func (m *Messenger) cancelPairing() {
	m.mu.Lock()
	c := m.pairing
	m.pairing = nil
	m.mu.Unlock()
	if c != nil {
		c()
	}
}

func (m *Messenger) adopt(cli *libgm.Client, ad *libgm.AuthData, phoneID string) {
	m.mu.Lock()
	old := m.client
	m.client = cli
	m.auth = ad
	m.mu.Unlock()
	if old != nil && old != cli {
		old.Disconnect()
	}
	m.saveSession()
	m.emit("paired", map[string]any{"phone": phoneID})
}

func friendlyPairError(err error) error {
	switch {
	case errors.Is(err, libgm.ErrNoDevicesFound):
		return errors.New("Google Messages on your phone isn't signed in to this Google account. In Google Messages, tap your profile picture › Device pairing, sign in with the same account, then try again")
	case errors.Is(err, libgm.ErrPhoneInactive):
		return errors.New("Google Messages on your phone hasn't connected to this Google account recently. In Google Messages, tap your profile picture › Device pairing and check it's the same account, then try again")
	case errors.Is(err, libgm.ErrPhoneNotFound):
		return errors.New("your phone isn't ready yet. Open Google Messages once, then try again")
	case errors.Is(err, libgm.ErrPairingInitTimeout):
		return errors.New("your phone didn't answer. Open Google Messages, tap your profile picture › Device pairing and make sure it's signed in with this same Google account. Then keep Google Messages open and try again")
	case errors.Is(err, events.ErrCallerNoPermission):
		return errors.New("this Google account can't use Google Messages for web")
	case errors.Is(err, libgm.ErrIncorrectEmoji):
		return errors.New("the wrong emoji was tapped")
	case errors.Is(err, libgm.ErrPairingCancelled):
		return errors.New("pairing was cancelled on the phone")
	case errors.Is(err, libgm.ErrPairingTimeout):
		return errors.New("pairing timed out")
	}
	return err
}

// Unpair signs this device out of Google Messages and forgets the session.
func (m *Messenger) Unpair() error {
	m.cancelPairing()
	m.mu.Lock()
	cli := m.client
	m.client = nil
	m.auth = nil
	m.mu.Unlock()
	var err error
	if cli != nil {
		ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
		err = cli.Unpair(ctx)
		cancel()
		cli.Disconnect()
	}
	_ = os.Remove(m.sessionPath())
	m.emit("unpaired", map[string]any{})
	return err
}

// ---------- reading ----------

func (m *Messenger) ready() (*libgm.Client, error) {
	m.mu.Lock()
	cli := m.client
	m.mu.Unlock()
	if cli == nil {
		return nil, errors.New("not connected")
	}
	return cli, nil
}

func ctx20() (context.Context, context.CancelFunc) {
	return context.WithTimeout(context.Background(), 20*time.Second)
}

func ctx60() (context.Context, context.CancelFunc) {
	return context.WithTimeout(context.Background(), 60*time.Second)
}

func ctx120() (context.Context, context.CancelFunc) {
	return context.WithTimeout(context.Background(), 120*time.Second)
}

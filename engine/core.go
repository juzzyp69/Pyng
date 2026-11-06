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
	"strings"
	"sync"
	"time"

	"github.com/rs/zerolog"
	"go.mau.fi/util/exhttp"

	"go.mau.fi/mautrix-gmessages/pkg/libgm"
	"go.mau.fi/mautrix-gmessages/pkg/libgm/events"
	"go.mau.fi/mautrix-gmessages/pkg/libgm/gmproto"
	"go.mau.fi/mautrix-gmessages/pkg/libgm/util"
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

	mu      sync.Mutex
	auth    *libgm.AuthData
	client  *libgm.Client
	convs   map[string]*gmproto.Conversation
	sims    []*gmproto.SIMCard
	pairing context.CancelFunc
}

// NewMessenger creates the engine. dataDir is a private app folder where the
// session and a rolling debug log are kept.
func NewMessenger(dataDir string, listener Listener) *Messenger {
	_ = os.MkdirAll(dataDir, 0o700)
	m := &Messenger{dir: dataDir, listener: listener, convs: map[string]*gmproto.Conversation{}}
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
	m.auth = m.loadSession()
	return m
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
func (m *Messenger) Start() error {
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
	if err := cli.Connect(context.Background()); err != nil {
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
func (m *Messenger) StartGooglePairing(cookiesJSON string) (string, error) {
	cookies := map[string]string{}
	if err := json.Unmarshal([]byte(cookiesJSON), &cookies); err != nil {
		return "", fmt.Errorf("bad cookies: %w", err)
	}
	for _, k := range []string{"SID", "HSID", "SSID", "APISID", "SAPISID", "OSID"} {
		if cookies[k] == "" {
			return "", fmt.Errorf("missing sign-in cookie %s", k)
		}
	}
	m.cancelPairing()
	ad := libgm.NewAuthData()
	ad.SetCookies(cookies)
	cli := m.newClient(ad)

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	if err := cli.FetchConfig(ctx); err != nil {
		return "", fmt.Errorf("could not reach Google Messages: %w", err)
	}
	bgCtx, bgCancel := context.WithCancel(context.Background())
	emoji, sess, err := cli.StartGaiaPairing(ctx, bgCtx)
	if err != nil {
		bgCancel()
		cli.Disconnect()
		return "", friendlyPairError(err)
	}
	m.mu.Lock()
	m.pairing = bgCancel
	m.mu.Unlock()

	go func() {
		defer bgCancel()
		phoneID, err := cli.FinishGaiaPairing(bgCtx, sess)
		if err != nil {
			cli.Disconnect()
			m.emit("pair_error", map[string]any{"error": friendlyPairError(err).Error()})
			return
		}
		m.adopt(cli, ad, phoneID)
		go func() {
			if err := cli.Reconnect(context.Background()); err != nil {
				m.log.Err(err).Msg("Reconnect after pairing failed")
			}
		}()
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
		return errors.New("no phone with Google Messages was found on this Google account")
	case errors.Is(err, libgm.ErrPhoneNotFound), errors.Is(err, libgm.ErrPhoneInactive):
		return errors.New("Google Messages on your phone hasn't been active recently; open it once and try again")
	case errors.Is(err, libgm.ErrPairingInitTimeout):
		return errors.New("Google Messages didn't respond; open it and try again")
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

// ListConversations returns up to count inbox conversations as a JSON array.
func (m *Messenger) ListConversations(count int) (string, error) {
	cli, err := m.ready()
	if err != nil {
		return "", err
	}
	ctx, cancel := ctx20()
	defer cancel()
	resp, err := cli.ListConversations(ctx, &gmproto.ListConversationsRequest{
		Count:  int64(count),
		Folder: gmproto.ListConversationsRequest_INBOX,
	})
	if err != nil {
		return "", err
	}
	out := make([]map[string]any, 0, len(resp.GetConversations()))
	m.mu.Lock()
	for _, c := range resp.GetConversations() {
		m.convs[c.GetConversationID()] = c
		out = append(out, convJSON(c))
	}
	m.mu.Unlock()
	return toJSON(out), nil
}

// FetchMessages returns the newest count messages of a conversation, oldest
// first, as a JSON array.
func (m *Messenger) FetchMessages(conversationID string, count int) (string, error) {
	cli, err := m.ready()
	if err != nil {
		return "", err
	}
	ctx, cancel := ctx20()
	defer cancel()
	resp, err := cli.FetchMessages(ctx, conversationID, int64(count), nil)
	if err != nil {
		return "", err
	}
	msgs := resp.GetMessages()
	out := make([]map[string]any, 0, len(msgs))
	for i := len(msgs) - 1; i >= 0; i-- {
		out = append(out, msgJSON(msgs[i]))
	}
	return toJSON(out), nil
}

// ---------- writing ----------

// SendText sends a plain text message. It returns the temporary id, which also
// appears on the message once Google Messages confirms it.
func (m *Messenger) SendText(conversationID string, text string) (string, error) {
	cli, err := m.ready()
	if err != nil {
		return "", err
	}
	m.mu.Lock()
	conv := m.convs[conversationID]
	m.mu.Unlock()
	if conv == nil {
		ctx, cancel := ctx20()
		conv, err = cli.GetConversation(ctx, conversationID)
		cancel()
		if err != nil {
			return "", fmt.Errorf("unknown conversation: %w", err)
		}
		m.mu.Lock()
		m.convs[conversationID] = conv
		m.mu.Unlock()
	}
	tmpID := util.GenerateTmpID()
	req := &gmproto.SendMessageRequest{
		ConversationID: conversationID,
		MessagePayload: &gmproto.MessagePayload{
			TmpID:          tmpID,
			TmpID2:         tmpID,
			ConversationID: conversationID,
			ParticipantID:  conv.GetDefaultOutgoingID(),
			MessageInfo: []*gmproto.MessageInfo{{
				Data: &gmproto.MessageInfo_MessageContent{MessageContent: &gmproto.MessageContent{Content: text}},
			}},
		},
		SIMPayload: conv.GetSimCard().GetSIMData().GetSIMPayload(),
		TmpID:      tmpID,
	}
	ctx, cancel := ctx20()
	defer cancel()
	resp, err := cli.SendMessage(ctx, req)
	if err != nil {
		return "", err
	}
	if resp.GetStatus() != gmproto.SendMessageResponse_SUCCESS {
		return "", fmt.Errorf("Google Messages refused the message (%s)", resp.GetStatus().String())
	}
	return tmpID, nil
}

// SendReaction adds (or with remove=true, removes) an emoji reaction.
func (m *Messenger) SendReaction(messageID string, emoji string, remove bool) error {
	cli, err := m.ready()
	if err != nil {
		return err
	}
	action := gmproto.SendReactionRequest_ADD
	if remove {
		action = gmproto.SendReactionRequest_REMOVE
	}
	ctx, cancel := ctx20()
	defer cancel()
	_, err = cli.SendReaction(ctx, &gmproto.SendReactionRequest{
		MessageID:    messageID,
		ReactionData: gmproto.MakeReactionData(emoji),
		Action:       action,
	})
	return err
}

// MarkRead marks a conversation as read up to messageID.
func (m *Messenger) MarkRead(conversationID string, messageID string) error {
	cli, err := m.ready()
	if err != nil {
		return err
	}
	ctx, cancel := ctx20()
	defer cancel()
	return cli.MarkRead(ctx, conversationID, messageID)
}

// DeleteMessage deletes a message (for you) in Google Messages too.
func (m *Messenger) DeleteMessage(messageID string) error {
	cli, err := m.ready()
	if err != nil {
		return err
	}
	ctx, cancel := ctx20()
	defer cancel()
	_, err = cli.DeleteMessage(ctx, messageID)
	return err
}

// ---------- events ----------

func (m *Messenger) handleEvent(evt any) {
	switch e := evt.(type) {
	case *libgm.WrappedMessage:
		m.emit("message", msgJSON(e.Message))
	case *gmproto.Conversation:
		m.mu.Lock()
		m.convs[e.GetConversationID()] = e
		m.mu.Unlock()
		m.emit("conversation", convJSON(e))
	case *gmproto.TypingData:
		m.emit("typing", map[string]any{
			"conversationId": e.GetConversationID(),
			"participantId":  e.GetUser().GetNumber(),
			"typing":         e.GetType() == gmproto.TypingTypes_STARTED_TYPING,
		})
	case *gmproto.Settings:
		m.mu.Lock()
		m.sims = e.GetSIMCards()
		m.mu.Unlock()
		m.emit("settings", map[string]any{"sims": len(e.GetSIMCards())})
	case *events.ClientReady:
		m.emit("connection", map[string]any{"state": "ready"})
	case *events.AuthTokenRefreshed:
		m.saveSession()
	case *events.PairSuccessful:
		m.mu.Lock()
		cli, ad := m.client, m.auth
		m.mu.Unlock()
		if e.QRData != nil && cli != nil {
			m.adopt(cli, ad, e.PhoneID)
		}
	case *events.PhoneNotResponding:
		m.emit("connection", map[string]any{"state": "phone_not_responding"})
	case *events.PhoneRespondingAgain:
		m.emit("connection", map[string]any{"state": "ready"})
	case *events.ListenTemporaryError:
		m.emit("connection", map[string]any{"state": "reconnecting", "error": e.Error.Error()})
	case *events.ListenRecovered:
		m.emit("connection", map[string]any{"state": "ready"})
	case *events.ListenFatalError:
		m.emit("connection", map[string]any{"state": "error", "error": e.Error.Error()})
	case *events.GaiaLoggedOut, *gmproto.RevokePairData:
		m.mu.Lock()
		m.auth = nil
		m.client = nil
		m.mu.Unlock()
		_ = os.Remove(m.sessionPath())
		m.emit("unpaired", map[string]any{"reason": "signed out on the phone"})
	case *gmproto.UserAlertEvent:
		m.emit("alert", map[string]any{"type": e.GetAlertType().String()})
	default:
		m.log.Debug().Type("type", evt).Msg("Unhandled event")
	}
}

func (m *Messenger) emit(kind string, payload any) {
	if m.listener == nil {
		return
	}
	defer func() {
		if r := recover(); r != nil {
			m.log.Error().Any("panic", r).Str("kind", kind).Msg("Listener panicked")
		}
	}()
	m.listener.OnEvent(kind, toJSON(payload))
}

// ReadLog returns the tail of the debug log, for sharing when something breaks.
func (m *Messenger) ReadLog(maxBytes int) string {
	data, err := os.ReadFile(filepath.Join(m.dir, "engine.log"))
	if err != nil {
		return ""
	}
	if len(data) > maxBytes {
		data = data[len(data)-maxBytes:]
	}
	return string(data)
}

// ---------- JSON shapes ----------

func convJSON(c *gmproto.Conversation) map[string]any {
	names := []string{}
	for _, p := range c.GetParticipants() {
		if !p.GetIsMe() && p.GetFullName() != "" {
			names = append(names, p.GetFullName())
		}
	}
	return map[string]any{
		"id":       c.GetConversationID(),
		"name":     c.GetName(),
		"preview":  c.GetLatestMessage().GetDisplayContent(),
		"fromMe":   c.GetLatestMessage().GetFromMe() == 1,
		"sender":   c.GetLatestMessage().GetDisplayName(),
		"ts":       c.GetLastMessageTimestamp() / 1000,
		"unread":   c.GetUnread(),
		"group":    c.GetIsGroupChat(),
		"rcs":      c.GetType() == gmproto.ConversationType_RCS,
		"color":    c.GetAvatarHexColor(),
		"pinned":   c.GetPinned(),
		"members":  names,
		"readOnly": c.GetReadOnly(),
	}
}

func msgJSON(msg *gmproto.Message) map[string]any {
	texts := []string{}
	media := ""
	for _, info := range msg.GetMessageInfo() {
		switch d := info.GetData().(type) {
		case *gmproto.MessageInfo_MessageContent:
			if t := d.MessageContent.GetContent(); t != "" {
				texts = append(texts, t)
			}
		case *gmproto.MessageInfo_MediaContent:
			media = d.MediaContent.GetMimeType()
		}
	}
	status := msg.GetMessageStatus().GetStatus().String()
	reactions := []map[string]any{}
	for _, r := range msg.GetReactions() {
		reactions = append(reactions, map[string]any{"emoji": r.GetData().GetUnicode(), "count": len(r.GetParticipantIDs())})
	}
	return map[string]any{
		"id":             msg.GetMessageID(),
		"tmpId":          msg.GetTmpID(),
		"conversationId": msg.GetConversationID(),
		"participantId":  msg.GetParticipantID(),
		"sender":         msg.GetSenderParticipant().GetFullName(),
		"fromMe":         strings.HasPrefix(status, "OUTGOING"),
		"status":         status,
		"text":           strings.Join(texts, "\n"),
		"media":          media,
		"ts":             msg.GetTimestamp() / 1000,
		"reactions":      reactions,
	}
}

func toJSON(v any) string {
	b, err := json.Marshal(v)
	if err != nil {
		return "{}"
	}
	return string(b)
}

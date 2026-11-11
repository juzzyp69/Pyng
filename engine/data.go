package core

import (
	"context"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/google/uuid"
	"google.golang.org/protobuf/proto"

	"go.mau.fi/mautrix-gmessages/pkg/libgm"
	gmcrypto "go.mau.fi/mautrix-gmessages/pkg/libgm/crypto"
	"go.mau.fi/mautrix-gmessages/pkg/libgm/events"
	"go.mau.fi/mautrix-gmessages/pkg/libgm/gmproto"
	"go.mau.fi/mautrix-gmessages/pkg/libgm/util"
)

// ---------- conversations ----------

func folderOf(name string) gmproto.ListConversationsRequest_Folder {
	switch name {
	case "archive":
		return gmproto.ListConversationsRequest_ARCHIVE
	case "spam":
		return gmproto.ListConversationsRequest_SPAM_BLOCKED
	}
	return gmproto.ListConversationsRequest_INBOX
}

// ListConversations returns up to count conversations from a folder ("inbox",
// "archive" or "spam") as a JSON array.
func (m *Messenger) ListConversations(folder string, count int) (res string, err error) {
	return m.ListConversationsBefore(folder, count, "", 0)
}

// ListConversationsBefore pages back through a folder: pass the id and timestamp (ms) of the
// oldest conversation already shown to get the ones before it.
func (m *Messenger) ListConversationsBefore(folder string, count int, beforeID string, beforeTs int64) (res string, err error) {
	defer m.guard("ListConversations", &err)
	cli, err := m.ready()
	if err != nil {
		return "", err
	}
	ctx, cancel := ctx20()
	defer cancel()
	req := &gmproto.ListConversationsRequest{
		Count:  int64(count),
		Folder: folderOf(folder),
	}
	if beforeID != "" {
		req.Cursor = &gmproto.Cursor{LastItemID: beforeID, LastItemTimestamp: beforeTs * 1000}
	}
	resp, err := cli.ListConversations(ctx, req)
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

// GetConversation returns one conversation as a JSON object.
func (m *Messenger) GetConversation(conversationID string) (res string, err error) {
	defer m.guard("GetConversation", &err)
	conv, err := m.conv(conversationID)
	if err != nil {
		return "", err
	}
	return toJSON(convJSON(conv)), nil
}

func (m *Messenger) conv(conversationID string) (*gmproto.Conversation, error) {
	m.mu.Lock()
	conv := m.convs[conversationID]
	m.mu.Unlock()
	if conv != nil {
		return conv, nil
	}
	cli, err := m.ready()
	if err != nil {
		return nil, err
	}
	ctx, cancel := ctx20()
	defer cancel()
	conv, err = cli.GetConversation(ctx, conversationID)
	if err != nil {
		return nil, fmt.Errorf("unknown conversation: %w", err)
	}
	m.mu.Lock()
	m.convs[conversationID] = conv
	m.mu.Unlock()
	return conv, nil
}

// FetchMessages returns up to count messages of a conversation, oldest first,
// as a JSON array. To page back, pass the id and timestamp (ms) of the oldest
// message already loaded; pass "" and 0 for the newest messages.
func (m *Messenger) FetchMessages(conversationID string, count int, beforeID string, beforeTs int64) (res string, err error) {
	defer m.guard("FetchMessages", &err)
	cli, err := m.ready()
	if err != nil {
		return "", err
	}
	var cursor *gmproto.Cursor
	if beforeID != "" {
		cursor = &gmproto.Cursor{LastItemID: beforeID, LastItemTimestamp: beforeTs * 1000}
	}
	ctx, cancel := ctx20()
	defer cancel()
	resp, err := cli.FetchMessages(ctx, conversationID, int64(count), cursor)
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

// ---------- sending ----------

func (m *Messenger) newRequest(conv *gmproto.Conversation, replyTo string) (*gmproto.SendMessageRequest, string) {
	tmpID := util.GenerateTmpID()
	req := &gmproto.SendMessageRequest{
		ConversationID: conv.GetConversationID(),
		MessagePayload: &gmproto.MessagePayload{
			TmpID:          tmpID,
			TmpID2:         tmpID,
			ConversationID: conv.GetConversationID(),
			ParticipantID:  conv.GetDefaultOutgoingID(),
		},
		SIMPayload: conv.GetSimCard().GetSIMData().GetSIMPayload(),
		TmpID:      tmpID,
	}
	if replyTo != "" {
		req.Reply = &gmproto.ReplyPayload{MessageID: replyTo}
	}
	return req, tmpID
}

func (m *Messenger) send(req *gmproto.SendMessageRequest) error {
	cli, err := m.ready()
	if err != nil {
		return err
	}
	ctx, cancel := ctx60()
	defer cancel()
	resp, err := cli.SendMessage(ctx, req)
	if err != nil {
		return err
	}
	if resp.GetStatus() != gmproto.SendMessageResponse_SUCCESS {
		return fmt.Errorf("Google Messages refused the message (%s)", resp.GetStatus().String())
	}
	return nil
}

// SendText sends a text message, optionally as a reply to replyTo (a message
// id, or ""). It returns the temporary id the message carries until Google
// Messages confirms it.
func (m *Messenger) SendText(conversationID string, text string, replyTo string) (res string, err error) {
	defer m.guard("SendText", &err)
	conv, err := m.conv(conversationID)
	if err != nil {
		return "", err
	}
	req, tmpID := m.newRequest(conv, replyTo)
	req.MessagePayload.MessageInfo = []*gmproto.MessageInfo{{
		Data: &gmproto.MessageInfo_MessageContent{MessageContent: &gmproto.MessageContent{Content: text}},
	}}
	if err = m.send(req); err != nil {
		return "", err
	}
	return tmpID, nil
}

// SendMedia uploads the file at path and sends it, with an optional caption.
func (m *Messenger) SendMedia(conversationID string, path string, mime string, fileName string, caption string, replyTo string) (res string, err error) {
	defer m.guard("SendMedia", &err)
	conv, err := m.conv(conversationID)
	if err != nil {
		return "", err
	}
	cli, err := m.ready()
	if err != nil {
		return "", err
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return "", fmt.Errorf("could not read the file: %w", err)
	}
	if fileName == "" {
		fileName = filepath.Base(path)
	}
	ctx, cancel := ctx60()
	media, err := cli.UploadMedia(ctx, data, fileName, mime)
	cancel()
	if err != nil {
		return "", fmt.Errorf("upload failed: %w", err)
	}
	req, tmpID := m.newRequest(conv, replyTo)
	req.MessagePayload.MessageInfo = []*gmproto.MessageInfo{{
		Data: &gmproto.MessageInfo_MediaContent{MediaContent: media},
	}}
	if caption != "" {
		req.MessagePayload.MessageInfo = append(req.MessagePayload.MessageInfo, &gmproto.MessageInfo{
			Data: &gmproto.MessageInfo_MessageContent{MessageContent: &gmproto.MessageContent{Content: caption}},
		})
	}
	if err = m.send(req); err != nil {
		return "", err
	}
	return tmpID, nil
}

// SendReaction adds (or with remove=true, removes) an emoji reaction.
func (m *Messenger) SendReaction(messageID string, emoji string, remove bool) (err error) {
	defer m.guard("SendReaction", &err)
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
func (m *Messenger) MarkRead(conversationID string, messageID string) (err error) {
	defer m.guard("MarkRead", &err)
	cli, err := m.ready()
	if err != nil {
		return err
	}
	ctx, cancel := ctx20()
	defer cancel()
	return cli.MarkRead(ctx, conversationID, messageID)
}

// SetTyping tells the other side you are typing.
func (m *Messenger) SetTyping(conversationID string) (err error) {
	defer m.guard("SetTyping", &err)
	conv, err := m.conv(conversationID)
	if err != nil {
		return err
	}
	cli, err := m.ready()
	if err != nil {
		return err
	}
	ctx, cancel := ctx20()
	defer cancel()
	return cli.SetTyping(ctx, conversationID, conv.GetSimCard().GetSIMData().GetSIMPayload())
}

// DeleteMessage deletes a message in Google Messages too.
func (m *Messenger) DeleteMessage(messageID string) (err error) {
	defer m.guard("DeleteMessage", &err)
	cli, err := m.ready()
	if err != nil {
		return err
	}
	ctx, cancel := ctx20()
	defer cancel()
	resp, err := cli.DeleteMessage(ctx, messageID)
	if err == nil && !resp.GetSuccess() {
		err = errors.New("Google Messages could not delete the message")
	}
	return err
}

// ---------- conversation actions ----------

func (m *Messenger) update(req *gmproto.UpdateConversationRequest) error {
	cli, err := m.ready()
	if err != nil {
		return err
	}
	ctx, cancel := ctx20()
	defer cancel()
	resp, err := cli.UpdateConversation(ctx, req)
	if err != nil {
		return err
	}
	if !resp.GetSuccess() {
		return errors.New("Google Messages on your phone rejected the change")
	}
	return nil
}

// SetArchived archives or unarchives a conversation.
func (m *Messenger) SetArchived(conversationID string, archived bool) (err error) {
	defer m.guard("SetArchived", &err)
	status := gmproto.ConversationStatus_ACTIVE
	if archived {
		status = gmproto.ConversationStatus_ARCHIVED
	}
	return m.update(&gmproto.UpdateConversationRequest{
		ConversationID: conversationID,
		Data: &gmproto.UpdateConversationRequest_UpdateData{UpdateData: &gmproto.UpdateConversationData{
			ConversationID: conversationID,
			Data:           &gmproto.UpdateConversationData_Status{Status: status},
		}},
	})
}

// SetMuted mutes or unmutes a conversation on the phone.
func (m *Messenger) SetMuted(conversationID string, muted bool) (err error) {
	defer m.guard("SetMuted", &err)
	mute := gmproto.ConversationMuteStatus_UNMUTE
	if muted {
		mute = gmproto.ConversationMuteStatus_MUTE
	}
	return m.update(&gmproto.UpdateConversationRequest{
		ConversationID: conversationID,
		Data: &gmproto.UpdateConversationRequest_UpdateData{UpdateData: &gmproto.UpdateConversationData{
			ConversationID: conversationID,
			Data:           &gmproto.UpdateConversationData_Mute{Mute: mute},
		}},
	})
}

func (m *Messenger) phoneOf(conversationID string) string {
	conv, err := m.conv(conversationID)
	if err != nil {
		return ""
	}
	for _, p := range conv.GetParticipants() {
		if !p.GetIsMe() && p.GetID().GetNumber() != "" {
			return p.GetID().GetNumber()
		}
	}
	return ""
}

// DeleteConversation deletes a whole conversation.
func (m *Messenger) DeleteConversation(conversationID string) (err error) {
	defer m.guard("DeleteConversation", &err)
	cli, err := m.ready()
	if err != nil {
		return err
	}
	ctx, cancel := ctx20()
	defer cancel()
	err = cli.DeleteConversation(ctx, conversationID, m.phoneOf(conversationID))
	if err == nil {
		m.mu.Lock()
		delete(m.convs, conversationID)
		m.mu.Unlock()
	}
	return err
}

// SetBlocked blocks (optionally reporting spam) or unblocks a conversation.
func (m *Messenger) SetBlocked(conversationID string, blocked bool, report bool) (err error) {
	defer m.guard("SetBlocked", &err)
	action := gmproto.ConversationActionStatus_UNBLOCK
	var action5 *gmproto.ConversationAction5
	if blocked {
		action = gmproto.ConversationActionStatus_BLOCK
		action5 = &gmproto.ConversationAction5{Field2: true}
		if report {
			action = gmproto.ConversationActionStatus_BLOCK_AND_REPORT
		}
	}
	req := &gmproto.UpdateConversationRequest{
		Action:         action,
		ConversationID: conversationID,
		Action5:        action5,
		Data: &gmproto.UpdateConversationRequest_DeleteData{DeleteData: &gmproto.DeleteConversationData{
			ConversationID: conversationID,
		}},
	}
	if phone := m.phoneOf(conversationID); phone != "" {
		req.GetDeleteData().Phone = &phone
	}
	return m.update(req)
}

// StartChat opens (or creates) a conversation with one or more numbers given as
// a JSON array of strings. groupName names a new RCS group (or "").
func (m *Messenger) StartChat(numbersJSON string, groupName string) (res string, err error) {
	defer m.guard("StartChat", &err)
	cli, err := m.ready()
	if err != nil {
		return "", err
	}
	var numbers []string
	if err = json.Unmarshal([]byte(numbersJSON), &numbers); err != nil || len(numbers) == 0 {
		return "", errors.New("no numbers given")
	}
	req := &gmproto.GetOrCreateConversationRequest{}
	for _, n := range numbers {
		req.Numbers = append(req.Numbers, &gmproto.ContactNumber{MysteriousInt: 2, Number: n, Number2: n})
	}
	if groupName != "" && len(numbers) > 1 {
		req.RCSGroupName = &groupName
	}
	ctx, cancel := ctx20()
	defer cancel()
	resp, err := cli.GetOrCreateConversation(ctx, req)
	if err != nil {
		return "", err
	}
	// For a new chat-features group, Google Messages first answers "this would be a new group" and
	// waits to be told to go ahead. Confirm it (a name is optional).
	if resp.GetStatus() == gmproto.GetOrCreateConversationResponse_CREATE_RCS {
		if req.RCSGroupName == nil {
			empty := ""
			req.RCSGroupName = &empty
		}
		yes := true
		req.CreateRCSGroup = &yes
		if resp, err = cli.GetOrCreateConversation(ctx, req); err != nil {
			return "", err
		}
	}
	conv := resp.GetConversation()
	if conv == nil {
		return "", errors.New("Google Messages did not open the conversation")
	}
	m.mu.Lock()
	m.convs[conv.GetConversationID()] = conv
	m.mu.Unlock()
	return toJSON(convJSON(conv)), nil
}

// ---------- contacts, avatars, media ----------

// ListContacts returns your phone's contacts as a JSON array.
func (m *Messenger) ListContacts() (res string, err error) {
	defer m.guard("ListContacts", &err)
	cli, err := m.ready()
	if err != nil {
		return "", err
	}
	ctx, cancel := ctx60()
	defer cancel()
	resp, err := cli.ListContacts(ctx)
	if err != nil {
		return "", err
	}
	out := make([]map[string]any, 0, len(resp.GetContacts()))
	for _, c := range resp.GetContacts() {
		out = append(out, map[string]any{
			"id":        c.GetParticipantID(),
			"contactId": c.GetContactID(),
			"name":      c.GetName(),
			"number":    c.GetNumber().GetNumber(),
			"formatted": c.GetNumber().GetFormattedNumber(),
			"color":     c.GetAvatarHexColor(),
		})
	}
	return toJSON(out), nil
}

func (m *Messenger) cacheDir(kind string) string {
	d := filepath.Join(m.dir, "cache", kind)
	_ = os.MkdirAll(d, 0o700)
	return d
}

func safeName(s string) string {
	sum := sha256.Sum256([]byte(s))
	return hex.EncodeToString(sum[:12])
}

// FetchAvatars downloads contact photos for participant ids (a JSON array) and
// returns a JSON object of id to file path. Ids without a photo map to "".
func (m *Messenger) FetchAvatars(participantIDsJSON string, contacts bool) (res string, err error) {
	defer m.guard("FetchAvatars", &err)
	cli, err := m.ready()
	if err != nil {
		return "", err
	}
	var ids []string
	if err = json.Unmarshal([]byte(participantIDsJSON), &ids); err != nil {
		return "", err
	}
	out := map[string]string{}
	if len(ids) == 0 {
		return toJSON(out), nil
	}
	dir := m.cacheDir("avatars")
	// One request per person, like Google Messages for Web: batched requests
	// don't reliably say which photo belongs to whom.
	got, failed := 0, 0
	defer func() {
		m.log.Info().Int("asked", len(ids)).Int("photos", got).Int("errors", failed).Bool("contacts", contacts).Msg("Avatar fetch")
	}()
	for _, id := range ids {
		out[id] = ""
		ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
		var resp *gmproto.GetThumbnailResponse
		var rerr error
		if contacts {
			resp, rerr = cli.GetContactThumbnail(ctx, id)
		} else {
			resp, rerr = cli.GetParticipantThumbnail(ctx, id)
		}
		cancel()
		if rerr != nil {
			failed++
			m.log.Warn().Err(rerr).Str("id", id).Msg("Avatar request failed")
			continue
		}
		for _, t := range resp.GetThumbnail() {
			buf := t.GetData().GetImageBuffer()
			if len(buf) == 0 {
				continue
			}
			p := filepath.Join(dir, safeName(id)+"-"+safeName(string(buf))[:8]+".jpg")
			if _, statErr := os.Stat(p); statErr != nil {
				if os.WriteFile(p, buf, 0o600) != nil {
					continue
				}
			}
			out[id] = p
			got++
			break
		}
	}
	return toJSON(out), nil
}

// DownloadMedia downloads and decrypts an attachment, caching it on disk, and
// returns the file path. keyB64 is the base64 decryption key from the message.
func (m *Messenger) DownloadMedia(mediaID string, keyB64 string, ext string) (res string, err error) {
	defer m.guard("DownloadMedia", &err)
	if ext != "" && !strings.HasPrefix(ext, ".") {
		ext = "." + ext
	}
	p := filepath.Join(m.cacheDir("media"), safeName(mediaID)+ext)
	if st, statErr := os.Stat(p); statErr == nil && st.Size() > 0 {
		return p, nil
	}
	cli, err := m.ready()
	if err != nil {
		return "", err
	}
	key, err := base64.StdEncoding.DecodeString(keyB64)
	if err != nil {
		return "", fmt.Errorf("bad media key: %w", err)
	}
	// Give up after 30s so a stuck download can be retried instead of spinning for minutes.
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	started := time.Now()
	rd, err := fetchMedia(ctx, cli, mediaID, key)
	if err != nil {
		m.log.Warn().Err(err).Str("ext", ext).Str("media", shortID(mediaID)).Dur("after", time.Since(started)).Msg("Attachment download failed")
		return "", err
	}
	defer rd.Close()
	tmp := p + ".part"
	f, err := os.Create(tmp)
	if err != nil {
		return "", err
	}
	n, err := io.Copy(f, rd)
	closeErr := f.Close()
	if err == nil {
		err = closeErr
	}
	if err == nil && n == 0 {
		err = errors.New("empty attachment")
	}
	if err != nil {
		_ = os.Remove(tmp)
		m.log.Warn().Err(err).Str("ext", ext).Str("media", shortID(mediaID)).Dur("after", time.Since(started)).Msg("Attachment download failed while reading")
		return "", err
	}
	m.log.Info().Str("ext", ext).Int64("bytes", n).Dur("after", time.Since(started)).Msg("Attachment downloaded")
	if err = os.Rename(tmp, p); err != nil {
		return "", err
	}
	return p, nil
}

// mediaHTTP downloads attachments. Headers must arrive quickly; the body can take longer.
var mediaHTTP = &http.Client{Transport: &http.Transport{
	Proxy:                 http.ProxyFromEnvironment,
	ResponseHeaderTimeout: 20 * time.Second,
	TLSHandshakeTimeout:   10 * time.Second,
	IdleConnTimeout:       60 * time.Second,
	MaxIdleConnsPerHost:   6,
}}

// fetchMedia asks Google for an attachment the same way Messages for web does, but checks the
// server's answer, so a refusal or a missing upload is reported clearly instead of as garbage.
func fetchMedia(ctx context.Context, cli *libgm.Client, mediaID string, key []byte) (io.ReadCloser, error) {
	cryptor, err := gmcrypto.NewAESGCMHelper(key)
	if err != nil {
		return nil, fmt.Errorf("bad media key: %w", err)
	}
	if cli.AuthData == nil {
		return nil, errors.New("not signed in")
	}
	meta := &gmproto.DownloadAttachmentRequest{
		Info: &gmproto.AttachmentInfo{AttachmentID: mediaID, Encrypted: true},
		AuthData: &gmproto.AuthMessage{
			RequestID:        uuid.NewString(),
			TachyonAuthToken: cli.AuthData.TachyonAuthToken,
			Network:          cli.AuthData.AuthNetwork(),
			ConfigVersion:    util.ConfigMessage,
		},
	}
	raw, err := proto.Marshal(meta)
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, util.UploadMediaURL, nil)
	if err != nil {
		return nil, err
	}
	util.BuildUploadHeaders(req, base64.StdEncoding.EncodeToString(raw))
	res, err := mediaHTTP.Do(req)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	if res.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(io.LimitReader(res.Body, 300))
		_ = res.Body.Close()
		return nil, fmt.Errorf("server answered %d: %s", res.StatusCode, strings.TrimSpace(string(body)))
	}
	return cryptor.DecryptStream(res.Body), nil
}

func shortID(s string) string {
	if len(s) > 10 {
		return s[:10]
	}
	return s
}

// RequestFullMedia asks your phone to upload the full-size version of an attachment that only
// arrived as a small preview. The full one shows up a little later as a message update.
func (m *Messenger) RequestFullMedia(messageID string, partID string) (err error) {
	defer m.guard("RequestFullMedia", &err)
	if messageID == "" || partID == "" {
		return errors.New("missing message or part")
	}
	cli, err := m.ready()
	if err != nil {
		return err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	_, err = cli.GetFullSizeImage(ctx, messageID, partID)
	if err != nil {
		m.log.Warn().Err(err).Msg("Asking the phone for the full attachment failed")
	} else {
		m.log.Info().Msg("Asked the phone for the full attachment")
	}
	return err
}

// LogNote writes a line from the app into the debug log.
func (m *Messenger) LogNote(text string) {
	m.log.Info().Str("from", "app").Msg(text)
}

// CacheSize returns the bytes used by cached photos and attachments.
func (m *Messenger) CacheSize() int64 {
	var total int64
	_ = filepath.Walk(filepath.Join(m.dir, "cache"), func(_ string, info os.FileInfo, err error) error {
		if err == nil && !info.IsDir() {
			total += info.Size()
		}
		return nil
	})
	return total
}

// ClearCache deletes cached photos and attachments. They download again when needed.
func (m *Messenger) ClearCache() {
	_ = os.RemoveAll(filepath.Join(m.dir, "cache", "media"))
}

// ---------- events ----------

func errText(err error) string {
	if err == nil {
		return ""
	}
	return err.Error()
}

// logOutgoingMedia records what the phone reports for attachments you send,
// so a log shows where a photo or video stopped (uploading, sending, failed).
func (m *Messenger) logOutgoingMedia(msg *gmproto.Message) {
	if msg == nil {
		return
	}
	st := msg.GetMessageStatus().GetStatus()
	if st < gmproto.MessageStatusType_OUTGOING_COMPLETE || st >= gmproto.MessageStatusType_INCOMING_COMPLETE {
		return
	}
	if len(msg.GetMessageInfo()) == 0 {
		m.log.Info().
			Str("message_id", msg.GetMessageID()).
			Str("tmp_id", msg.GetTmpID()).
			Str("status", st.String()).
			Msg("Sent message update with no content yet")
		return
	}
	for _, part := range msg.GetMessageInfo() {
		media := part.GetMediaContent()
		if media == nil {
			continue
		}
		m.log.Info().
			Str("message_id", msg.GetMessageID()).
			Str("tmp_id", msg.GetTmpID()).
			Str("status", st.String()).
			Str("status_text", msg.GetMessageStatus().GetStatusText()).
			Str("mime", media.GetMimeType()).
			Int64("size", media.GetSize()).
			Bool("has_media_id", media.GetMediaID() != "").
			Msg("Sent attachment update")
	}
}

func (m *Messenger) handleEvent(evt any) {
	defer m.guard(fmt.Sprintf("event %T", evt), nil)
	switch e := evt.(type) {
	case *libgm.WrappedMessage:
		m.logOutgoingMedia(e.Message)
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
		sims := []map[string]any{}
		for _, s := range e.GetSIMCards() {
			sims = append(sims, map[string]any{
				"name":   s.GetSIMData().GetCarrierName(),
				"number": s.GetSIMData().GetFormattedPhoneNumber(),
				"rcs":    s.GetRCSChats().GetEnabled(),
			})
		}
		m.emit("settings", map[string]any{"sims": sims})
	case *events.ClientReady:
		m.lastActive.Store(time.Now().UnixMilli())
		m.emit("connection", map[string]any{"state": "ready"})
	case *events.NoDataReceived:
		go m.nudge("no data")
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
		m.emit("connection", map[string]any{"state": "reconnecting", "error": errText(e.Error)})
	case *events.ListenRecovered:
		m.emit("connection", map[string]any{"state": "ready"})
	case *events.ListenFatalError:
		m.emit("connection", map[string]any{"state": "error", "error": errText(e.Error)})
	case *events.GaiaLoggedOut, *gmproto.RevokePairData:
		m.mu.Lock()
		m.auth = nil
		m.client = nil
		m.mu.Unlock()
		_ = os.Remove(m.sessionPath())
		m.emit("unpaired", map[string]any{"reason": "signed out on the phone"})
	case *gmproto.UserAlertEvent:
		switch e.GetAlertType() {
		case gmproto.AlertType_BROWSER_ACTIVE:
			m.lastActive.Store(time.Now().UnixMilli())
			m.emit("connection", map[string]any{"state": "ready"})
		case gmproto.AlertType_BROWSER_INACTIVE, gmproto.AlertType_BROWSER_INACTIVE_FROM_TIMEOUT, gmproto.AlertType_BROWSER_INACTIVE_FROM_INACTIVITY:
			// Another session (like Messages for web) took over, or the phone timed Pyng out.
			// Take it back while Pyng is on screen.
			if m.fg.Load() {
				go func() { time.Sleep(2 * time.Second); m.nudge("inactive: " + e.GetAlertType().String()) }()
			}
		}
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

func participantJSON(p *gmproto.Participant) map[string]any {
	return map[string]any{
		"id":        p.GetID().GetParticipantID(),
		"number":    p.GetID().GetNumber(),
		"formatted": p.GetFormattedNumber(),
		"name":      p.GetFullName(),
		"first":     p.GetFirstName(),
		"color":     p.GetAvatarHexColor(),
		"me":        p.GetIsMe(),
		"contactId": p.GetContactID(),
	}
}

func convJSON(c *gmproto.Conversation) map[string]any {
	people := []map[string]any{}
	for _, p := range c.GetParticipants() {
		people = append(people, participantJSON(p))
	}
	status := "active"
	switch c.GetStatus() {
	case gmproto.ConversationStatus_ARCHIVED, gmproto.ConversationStatus_KEEP_ARCHIVED:
		status = "archived"
	case gmproto.ConversationStatus_SPAM_FOLDER:
		status = "spam"
	case gmproto.ConversationStatus_BLOCKED_FOLDER:
		status = "blocked"
	case gmproto.ConversationStatus_DELETED, gmproto.ConversationStatus_TRASH_FOLDER:
		status = "deleted"
	}
	return map[string]any{
		"id":         c.GetConversationID(),
		"name":       c.GetName(),
		"preview":    c.GetLatestMessage().GetDisplayContent(),
		"fromMe":     c.GetLatestMessage().GetFromMe() == 1,
		"sender":     c.GetLatestMessage().GetDisplayName(),
		"lastStatus": c.GetLatestMessage().GetLatestMessageStatus().GetStatus().String(),
		"ts":         c.GetLastMessageTimestamp() / 1000,
		"unread":     c.GetUnread(),
		"group":      c.GetIsGroupChat(),
		"rcs":        c.GetType() == gmproto.ConversationType_RCS,
		"smsOnly":    c.GetSendMode() != gmproto.ConversationSendMode_SEND_MODE_AUTO,
		"color":      c.GetAvatarHexColor(),
		"pinned":     c.GetPinned(),
		"readOnly":   c.GetReadOnly(),
		"status":     status,
		"people":     people,
		"others":     c.GetOtherParticipants(),
		"groupPhoto": c.GetGroupAvatarURL(),
	}
}

// inlineDir is where attachments that arrive inside a message are written, so the app can show them.
var inlineDir string

func msgJSON(msg *gmproto.Message) map[string]any {
	texts := []string{}
	media := []map[string]any{}
	unknown := 0
	addMedia := func(mc *gmproto.MediaContent, part string) {
		if mc == nil {
			return
		}
		// Some attachments arrive without a type; the format code still says what they are.
		mime := mc.GetMimeType()
		mt, known := libgm.FormatToMediaType[mc.GetFormat()]
		if mime == "" && known {
			mime = mt.Format
		}
		if mime == "" {
			switch mc.GetFormat() {
			case gmproto.MediaFormats_IMAGE_UNSPECIFIED:
				mime = "image/jpeg"
			case gmproto.MediaFormats_VIDEO_UNSPECIFIED:
				mime = "video/mp4"
			case gmproto.MediaFormats_AUDIO_UNSPECIFIED:
				mime = "audio/mp4"
			}
		}
		item := map[string]any{
			"id":       mc.GetMediaID(),
			"key":      base64.StdEncoding.EncodeToString(mc.GetDecryptionKey()),
			"thumbId":  mc.GetThumbnailMediaID(),
			"thumbKey": base64.StdEncoding.EncodeToString(mc.GetThumbnailDecryptionKey()),
			"mime":     mime,
			"name":     mc.GetMediaName(),
			"size":     mc.GetSize(),
			"w":        mc.GetDimensions().GetWidth(),
			"h":        mc.GetDimensions().GetHeight(),
			"msg":      msg.GetMessageID(),
			"part":     part,
		}
		// Bytes inside the message itself. When there's also something to download, they're only a
		// tiny blurry placeholder, so they're offered as a preview; the real file still downloads.
		// Only when there's nothing else (as the Google Messages bridge does) are they the file.
		if data := mc.GetMediaData(); len(data) > 0 && inlineDir != "" {
			only := mc.GetMediaID() == "" && mc.GetThumbnailMediaID() == ""
			ext := "jpg"
			if only && mt.Extension != "" {
				ext = strings.TrimPrefix(mt.Extension, ".")
			}
			name := "preview-"
			if only {
				name = "inline-"
			}
			p := filepath.Join(inlineDir, safeName(name+msg.GetMessageID()+"-"+part)+"."+ext)
			ok := false
			if st, err := os.Stat(p); err == nil && st.Size() > 0 {
				ok = true
			} else if err := os.WriteFile(p, data, 0o600); err == nil {
				ok = true
			}
			if ok && only {
				item["local"] = p
			} else if ok {
				item["preview"] = p
			}
		}
		media = append(media, item)
	}
	for _, info := range msg.GetMessageInfo() {
		switch d := info.GetData().(type) {
		case *gmproto.MessageInfo_MessageContent:
			if t := d.MessageContent.GetContent(); t != "" {
				texts = append(texts, t)
			}
		case *gmproto.MessageInfo_MediaContent:
			addMedia(d.MediaContent, info.GetActionMessageID())
		case *gmproto.MessageInfo_LocationContent:
			if t := d.LocationContent.GetMessageContent().GetContent(); t != "" {
				texts = append(texts, t)
			}
			addMedia(d.LocationContent.GetMediaContent(), info.GetActionMessageID())
		default:
			unknown++
		}
	}
	st := msg.GetMessageStatus().GetStatus()
	status := st.String()
	reactions := []map[string]any{}
	for _, r := range msg.GetReactions() {
		reactions = append(reactions, map[string]any{
			"emoji": r.GetData().GetUnicode(),
			"count": len(r.GetParticipantIDs()),
			"by":    r.GetParticipantIDs(),
		})
	}
	kind := "text"
	if st >= 200 {
		kind = "sys"
	}
	return map[string]any{
		"id":             msg.GetMessageID(),
		"tmpId":          msg.GetTmpID(),
		"conversationId": msg.GetConversationID(),
		"participantId":  msg.GetParticipantID(),
		"sender":         msg.GetSenderParticipant().GetFullName(),
		"senderFirst":    msg.GetSenderParticipant().GetFirstName(),
		"fromMe":         strings.HasPrefix(status, "OUTGOING"),
		"status":         status,
		"statusText":     msg.GetMessageStatus().GetStatusText(),
		"text":           strings.Join(texts, "\n"),
		"media":          media,
		"ts":             msg.GetTimestamp() / 1000,
		"reactions":      reactions,
		"replyTo":        msg.GetReplyMessage().GetMessageID(),
		"type":           msg.GetType(),
		"kind":           kind,
		"unknown":        unknown,
	}
}

func toJSON(v any) string {
	b, err := json.Marshal(v)
	if err != nil {
		return "{}"
	}
	return string(b)
}

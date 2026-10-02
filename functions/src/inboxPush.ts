// Phone notification for a new invite. The app writes the invite into the target's inbox (see firestore.rules); this trigger
// turns that document into a push, so the notification arrives even when the app is closed. Needs the Blaze plan to deploy.
// Without it the app still shows the invite in its list and raises a notification by itself while it is running.

import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { push } from "./push";

export const onInviteInboxCreated = onDocumentCreated("users/{uid}/inbox/{fromUid}", async (event) => {
  const data = event.data?.data();
  if (!data) return;
  const uid = event.params.uid;
  await push(uid, {
    type: "friend_invite_inbox",
    fromUid: String(data.fromUid ?? event.params.fromUid),
    fromName: String(data.fromName ?? data.fromUsername ?? ""),
    fromUsername: String(data.fromUsername ?? ""),
  });
});

// When somebody goes live in Broadcast, every phone that listens to the "broadcasts" topic gets a push, even with the app closed.
// The app also raises the same notification itself while it is running (same notification id, so nobody sees two).
// Needs the Blaze plan to deploy. A phone joins the topic when "Broadcast alert" is on in Settings (the default).

import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { getMessaging } from "firebase-admin/messaging";

export const onBroadcastCreated = onDocumentCreated("broadcasts/{room}", async (event) => {
  const data = event.data?.data();
  if (!data) return;
  await getMessaging().send({
    topic: "broadcasts",
    // Data only, high priority: the app decides whether to show it (host, switch off) and builds the text itself.
    data: {
      type: "broadcast_live",
      roomId: event.params.room,
      hostUid: String(data.hostUid ?? ""),
      hostName: String(data.hostName ?? "").slice(0, 30),
      title: String(data.title ?? "").slice(0, 40),
    },
    android: { priority: "high", ttl: 10 * 60 * 1000 },
  });
});

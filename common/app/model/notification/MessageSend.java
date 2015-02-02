package model.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.User;
import play.Logger;
import play.libs.ws.WS;
import play.libs.ws.WSResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MessageSend {

    public static class MandrillMessageRequest {

        public String key = play.Play.application().configuration().getString("mandrill_key");
        public MandrillMessage message;
        public Boolean async;

        @JsonProperty("ip_pool")
        public String ipPool;

        @JsonProperty("send_at")
        public String sendAt;


        public boolean sendCall() throws JsonProcessingException {
            String url = "/messages/send.json";
            WSResponse response = WS.url(_MANDRILL_URL + url).post(new ObjectMapper().writeValueAsString(this)).get(5000, TimeUnit.MILLISECONDS);

            if (response.getStatus() == 200) {
                Logger.info("Mandrill Response:" + response.asJson().toString());
            }
            else {
                Logger.error("Mandrill Response:" + response.asJson().toString());
            }
            return response.getStatus() == 200; // You can consider any non-200 HTTP response code an error - the returned data will contain more detailed information
        }

        private final String _MANDRILL_URL = "https://mandrillapp.com/api/1.0";
    }

    public static class MandrillMessage {

        public static class Recipient {
            public enum Type {
                TO  ("to"),
                BCC ("bcc"),
                CC  ("cc");

                private String key;

                Type(String key) {
                   this.key = key;
                }

                @JsonValue
                public String getKey() {
                    return key;
                }
            }
            public String name;
            public Type type = Type.TO;
            public String email;
        }

        public static class MessageContent {
            public String name, type, content;
            public Boolean binary;
        }

        public static class MergeVarBucket {
            public String rcpt;
            public List<MergeVar> vars;
        }

        public static class MergeVar {
            public String name, content;
        }

        public static class RecipientMetadata {
            public String rcpt;
            public Map<String,String> values;
        }

        public String subject, html, text;
        public List<Recipient> to;
        public Map<String,String> headers;
        public Boolean important;
        public Boolean merge;
        public List<String> tags;
        public String subaccount;
        public List<MessageContent> attachments;
        public List<MessageContent> images;
        public Map<String,String> metadata;

        @JsonProperty("from_email")
        public String  fromEmail;

        @JsonProperty("from_name")
        public String  fromName;

        @JsonProperty("track_opens")
        public Boolean trackOpens;

        @JsonProperty("track_clicks")
        public Boolean trackClicks;

        @JsonProperty("auto_text")
        public Boolean autoText;

        @JsonProperty("auto_html")
        public Boolean autoHtml;

        @JsonProperty("inline_css")
        public Boolean inlineCss;

        @JsonProperty("url_strip_qs")
        public Boolean urlStripQs;

        @JsonProperty("preserve_recipients")
        public Boolean preserveRecipients;

        @JsonProperty("view_content_link")
        public Boolean viewContentLink;

        @JsonProperty("bcc_address")
        public String bccAddress;

        @JsonProperty("tracking_domain")
        public String trackingDomain;

        @JsonProperty("signing_domain")
        public String signingDomain;

        @JsonProperty("return_path_domain")
        public String returnPathDomain;

        @JsonProperty("global_merge_vars")
        public List<MergeVar> globalMergeVars;

        @JsonProperty("merge_vars")
        public List<MergeVarBucket> mergeVars;

        @JsonProperty("google_analytics_domains")
        public List<String> googleAnalyticsDomains;

        @JsonProperty("google_analytics_campaign")
        public String googleAnalyticsCampaign;

        @JsonProperty("recipient_metadata")
        public List<RecipientMetadata> recipientMetadata;

    }

    public static boolean send(String recipientName, String recipientEmail, String subject, String html) {
        Map<String, String> recipient = new HashMap<>();
        recipient.put(recipientEmail, recipientName);
        return send(recipient, subject, html);
    }


    public static boolean send(Map<String, String> recipients, String subject, String html) {
        /**
         * recipients: Mapa con clave email, contenido nombre del destinatario.
         */
        ArrayList<MandrillMessage.Recipient> to = new ArrayList<>();
        for (String recipientsKey : recipients.keySet()) {
            MandrillMessage.Recipient fulano = new MandrillMessage.Recipient();
            fulano.email = recipientsKey;
            fulano.name = recipients.get(recipientsKey);
            to.add(fulano);
        }

        MandrillMessage message = new MandrillMessage();
        message.to = to;
        message.fromEmail = "support@epiceleven.com";
        message.fromName = "Epic Eleven";

        message.html = html;
        message.subject = subject;

        message.trackClicks = true;
        message.trackOpens = true;

        MandrillMessageRequest messageRequest = new MandrillMessageRequest();
        messageRequest.message = message;
        messageRequest.async = false;
        messageRequest.ipPool = "Main Pool";

        try {
            return messageRequest.sendCall();
        }
        catch (JsonProcessingException e) {
            Logger.error("WTF 9205");
        }
        return false;
    }



    public static void notifyIfNotYetNotified(User user, Notification.Topic topic, String subject, String htmlContent) {
        if (Notification.isNotSent(topic, htmlContent, user.userId)) {
            Notification notification = new Notification(topic, htmlContent, user.userId);
            if (send(user.nickName, user.email, subject, htmlContent)) {
                notification.markSent();
            }
        }
    }

}

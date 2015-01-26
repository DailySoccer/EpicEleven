package model.emailing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import play.Logger;
import play.libs.F;
import play.libs.ws.WS;
import play.libs.ws.WSResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageSend {

    public static class MandrillMessageRequest {

        public String key = play.Play.application().configuration().getString("mandrill_key");
        public MandrillMessage message;
        public Boolean async;

        @JsonProperty("ip_pool")
        public String ipPool;

        @JsonProperty("send_at")
        public String sendAt;


        public F.Promise<Boolean> sendCall() {
            String url = "/messages/send.json";
            try {
                ObjectMapper mapper = new ObjectMapper();
                return WS.url(_mandrillUrl+url).post(mapper.writeValueAsString(this)).map(
                        new F.Function<WSResponse, Boolean>() {
                            public Boolean apply(WSResponse response) {
                                if (response.getStatus() == 200) {
                                    Logger.info("Mandrill Response:" + response.asJson().toString());
                                }
                                else {
                                    Logger.error("Mandrill Response:" + response.asJson().toString());
                                }
                                return response.getStatus() == 200;
                            }
                        }
                );

            } catch (Exception e) {
                Logger.error("WTF 5112");
            }
            return null;
        }


        private final String _mandrillUrl = "https://mandrillapp.com/api/1.0";
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
            public MergeVar[] vars;
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

    public static void send(String recipientName, String recipientEmail, String subject, String html) {
        Map<String, String> recipient = new HashMap<>();
        recipient.put(recipientEmail, recipientName);
        send(recipient, subject, html);
    }


    public static void send(Map<String, String> recipients, String subject, String html) {
        ArrayList<MandrillMessage.Recipient> to = new ArrayList<>();
        for (String asdf : recipients.keySet()) {
            MandrillMessage.Recipient fulano = new MandrillMessage.Recipient();
            fulano.email = asdf;
            fulano.name = recipients.get(asdf);
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

        ObjectMapper mapper = new ObjectMapper();
        try {
            Logger.info(mapper.writeValueAsString(messageRequest));
            Logger.info(String.valueOf(messageRequest.sendCall()));
        } catch (JsonProcessingException e) {
            Logger.error("WTF 9205");
        }
    }


}

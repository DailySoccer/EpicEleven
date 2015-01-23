package model.emailing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import play.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MessageSend {

    private static class MandrillMessageRequest {

        public MandrillMessage getMessage() {
            return message;
        }

        public void setMessage(MandrillMessage message) {
            this.message = message;
        }

        public Boolean getAsync() {
            return async;
        }

        public void setAsync(Boolean async) {
            this.async = async;
        }

        public String getIpPool() {
            return ip_pool;
        }

        public void setIpPool(String ip_pool) {
            this.ip_pool = ip_pool;
        }

        public String getSendAt() {
            return send_at;
        }

        public void setSendAt(String send_at) {
            this.send_at = send_at;
        }

        private String key = play.Play.application().configuration().getString("mandrill_key");
        private MandrillMessage message;
        private Boolean async;
        private String ip_pool;
        private String send_at;
    }

    private static class MandrillMessage {

        public static class Recipient {

            private String name;
            private Type type = Type.TO;

            public enum Type {
                TO, BCC, CC
            }

            private String email;

            public String getEmail() {
                return email;
            }

            public void setEmail(String email) {
                this.email = email;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public Type getType() {
                return type;
            }

            public void setType(Type type) {
                this.type = type;
            }


        }

        public static class MessageContent {

            private String name, type, content;
            private Boolean binary;

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            public String getContent() {
                return content;
            }

            public void setContent(String content) {
                this.content = content;
            }

            public Boolean getBinary() {
                return binary;
            }

            public void setBinary(Boolean binary) {
                this.binary = binary;
            }

        }

        public static class MergeVarBucket {

            private String rcpt;
            private MergeVar[] vars;


            public MergeVar[] getVars() {
                return vars;
            }

            public void setVars(MergeVar[] vars) {
                this.vars = vars;
            }

            public String getRcpt() {
                return rcpt;
            }

            public void setRcpt(String rcpt) {
                this.rcpt = rcpt;
            }

        }

        public static class MergeVar {

            private String name, content;

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getContent() {
                return content;
            }

            public void setContent(String content) {
                this.content = content;
            }

        }

        public static class RecipientMetadata {

            private String rcpt;
            private Map<String,String> values;

            public String getRcpt() {
                return rcpt;
            }

            public void setRcpt(String rcpt) {
                this.rcpt = rcpt;
            }

            public Map<String, String> getValues() {
                return values;
            }

            public void setValues(Map<String, String> values) {
                this.values = values;
            }

        }


        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getHtml() {
            return html;
        }

        public void setHtml(String html) {
            this.html = html;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getFromEmail() {
            return from_email;
        }

        public void setFromEmail(String from_email) {
            this.from_email = from_email;
        }

        public String getFromName() {
            return from_name;
        }

        public void setFromName(String from_name) {
            this.from_name = from_name;
        }

        public List<Recipient> getTo() {
            return to;
        }

        public void setTo(List<Recipient> to) {
            this.to = to;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }

        public Boolean getImportant() {
            return important;
        }

        public void setImportant(Boolean important) {
            this.important = important;
        }

        public Boolean getTrackOpens() {
            return track_opens;
        }

        public void setTrackOpens(Boolean track_opens) {
            this.track_opens = track_opens;
        }

        public Boolean getTrackClicks() {
            return track_clicks;
        }

        public void setTrackClicks(Boolean track_clicks) {
            this.track_clicks = track_clicks;
        }

        public Boolean getAutoText() {
            return auto_text;
        }

        public void setAutoText(Boolean auto_text) {
            this.auto_text = auto_text;
        }

        public Boolean getAutoHtml() {
            return auto_html;
        }

        public void setAutoHtml(Boolean auto_html) {
            this.auto_html = auto_html;
        }

        public Boolean getInlineCss() {
            return inline_css;
        }

        public void setInlineCss(Boolean inline_css) {
            this.inline_css = inline_css;
        }

        public Boolean getUrlStripQs() {
            return url_strip_qs;
        }

        public void setUrlStripQs(Boolean url_strip_qs) {
            this.url_strip_qs = url_strip_qs;
        }

        public Boolean getPreserveRecipients() {
            return preserve_recipients;
        }

        public void setPreserveRecipients(Boolean preserve_recipients) {
            this.preserve_recipients = preserve_recipients;
        }

        public Boolean getViewContentLink() {
            return view_content_link;
        }

        public void setViewContentLink(Boolean view_content_link) {
            this.view_content_link = view_content_link;
        }

        public String getBccAddress() {
            return bcc_address;
        }

        public void setBccAddress(String bcc_address) {
            this.bcc_address = bcc_address;
        }

        public String getTrackingDomain() {
            return tracking_domain;
        }

        public void setTrackingDomain(String tracking_domain) {
            this.tracking_domain = tracking_domain;
        }

        public String getSigningDomain() {
            return signing_domain;
        }

        public void setSigningDomain(String signing_domain) {
            this.signing_domain = signing_domain;
        }

        public String getReturnPathDomain() {
            return return_path_domain;
        }

        public void setReturnPathDomain(String return_path_domain) {
            this.return_path_domain = return_path_domain;
        }

        public Boolean getMerge() {
            return merge;
        }

        public void setMerge(Boolean merge) {
            this.merge = merge;
        }

        public List<MergeVar> getGlobalMergeVars() {
            return global_merge_vars;
        }

        public void setGlobalMergeVars(List<MergeVar> global_merge_vars) {
            this.global_merge_vars = global_merge_vars;
        }

        public List<MergeVarBucket> getMergeVars() {
            return merge_vars;
        }

        public void setMergeVars(List<MergeVarBucket> merge_vars) {
            this.merge_vars = merge_vars;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public String getSubaccount() {
            return subaccount;
        }

        public void setSubaccount(String subaccount) {
            this.subaccount = subaccount;
        }

        public List<String> getGoogleAnalyticsDomains() {
            return google_analytics_domains;
        }

        public void setGoogleAnalyticsDomains(List<String> google_analytics_domains) {
            this.google_analytics_domains = google_analytics_domains;
        }

        public String getGoogleAnalyticsCampaign() {
            return google_analytics_campaign;
        }

        public void setGoogleAnalyticsCampaign(String google_analytics_campaign) {
            this.google_analytics_campaign = google_analytics_campaign;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }

        public List<RecipientMetadata> getRecipientMetadata() {
            return recipient_metadata;
        }

        public void setRecipientMetadata(List<RecipientMetadata> recipient_metadata) {
            this.recipient_metadata = recipient_metadata;
        }

        public List<MessageContent> getAttachments() {
            return attachments;
        }

        public void setAttachments(List<MessageContent> attachments) {
            this.attachments = attachments;
        }

        public List<MessageContent> getImages() {
            return images;
        }

        public void setImages(List<MessageContent> images) {
            this.images = images;
        }

        private String subject, html, text, from_email, from_name;
        private List<Recipient> to;
        private Map<String,String> headers;
        private Boolean important, track_opens, track_clicks, auto_text, auto_html,
                inline_css, url_strip_qs, preserve_recipients, view_content_link;
        private String bcc_address, tracking_domain, signing_domain,
                return_path_domain;
        private Boolean merge;
        private List<MergeVar> global_merge_vars;
        private List<MergeVarBucket> merge_vars;
        private List<String> tags;
        private String subaccount;
        private List<String> google_analytics_domains;
        private String google_analytics_campaign;
        private Map<String,String> metadata;
        private List<RecipientMetadata> recipient_metadata;
        private List<MessageContent> attachments;
        private List<MessageContent> images;
    }

    public static void test() {

        MandrillMessage message = new MandrillMessage();
        message.setSubaccount("Algo");

        ArrayList<MandrillMessage.Recipient> to = new ArrayList<>();

        MandrillMessage.Recipient fulano = new MandrillMessage.Recipient();
        fulano.setEmail("fulano@mailinator.com");
        fulano.setType(MandrillMessage.Recipient.Type.BCC);
        fulano.setName("Fulanito");

        to.add(fulano);

        message.setTo(to);

        MandrillMessageRequest messageRequest = new MandrillMessageRequest();
        messageRequest.setAsync(false);
        messageRequest.setIpPool("Main Pool");
        messageRequest.setMessage(message);


        ObjectMapper mapper = new ObjectMapper();
        try {
            Logger.info(mapper.writeValueAsString(messageRequest));
        } catch (JsonProcessingException e) {
            Logger.error("WTF 9205");
        }

    }



}

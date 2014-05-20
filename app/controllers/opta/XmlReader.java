package controllers.opta;

import org.w3c.dom.Document;

/**
 * Created by gnufede on 20/05/14.
 */
public class XmlReader {
    private String body;
    private Document dom;

    public XmlReader (String body){
        this.body = body;
        this.dom = play.libs.XML.fromString(body.substring(body.indexOf('<')));
    }



}

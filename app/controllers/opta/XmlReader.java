package controllers.opta;

import org.w3c.dom.Document;

//import com.fasterxml.aalto.AsyncXMLStreamReader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.XMLEvent;

import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import com.fasterxml.aalto.stax.InputFactoryImpl;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Created by gnufede on 20/05/14.
 */
public class XmlReader {
    private String body;
    //private Document dom;

    private void execute(String xmlString) throws Exception {
        InputStream xmlInputStream = new ByteArrayInputStream(xmlString.getBytes(StandardCharsets.UTF_8));
        //Load Aalto's StAX parser factory
        InputFactoryImpl factory = new InputFactoryImpl();
//        XMLInputFactory2 xmlInputFactory = (XMLInputFactory2) XMLInputFactory.newFactory("com.fasterxml.aalto.stax.InputFactoryImpl", this.getClass().getClassLoader());
        XMLStreamReader2 xmlStreamReader = (XMLStreamReader2) factory.createXMLStreamReader(xmlInputStream);
        while(xmlStreamReader.hasNext()){
            int eventType = xmlStreamReader.next();
            switch (eventType) {
                case XMLEvent.START_ELEMENT:
                    //System.out.print("<" + xmlStreamReader.getName().toString() + ">");
                    break;
                case XMLEvent.CHARACTERS:
                    //System.out.print(xmlStreamReader.getText());
                    break;
                case XMLEvent.END_ELEMENT:
                    //System.out.println("</" + xmlStreamReader.getName().toString() + ">");
                    break;
                default:
                    //do nothing
                    break;
            }
        }
    }

    public XmlReader (String body){
        this.body = body;
//        String xml_str = play.libs.XML.fromString(body.substring(body.indexOf('<')));
        try {
            this.execute(body.substring(body.indexOf('<')));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



}

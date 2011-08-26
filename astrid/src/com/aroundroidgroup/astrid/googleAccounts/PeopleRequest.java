package com.aroundroidgroup.astrid.googleAccounts;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicNameValuePair;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class PeopleRequest {

    private static List<NameValuePair> createPostData(FriendProps myFp,String peopleString){
        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(4);
        if (myFp!=null && myFp.isValid()){
            nameValuePairs.add(new BasicNameValuePair("GPSLAT", myFp.getLat())); //$NON-NLS-1$
            nameValuePairs.add(new BasicNameValuePair("GPSLON", myFp.getLon())); //$NON-NLS-1$
            nameValuePairs.add(new BasicNameValuePair("TIMESTAMP",myFp.getTime())); //$NON-NLS-1$
        }
        else{
            nameValuePairs.add(new BasicNameValuePair("GPSLAT", String.valueOf(0.0))); //$NON-NLS-1$
            nameValuePairs.add(new BasicNameValuePair("GPSLON", String.valueOf(0.0))); //$NON-NLS-1$
            nameValuePairs.add(new BasicNameValuePair("TIMESTAMP", String.valueOf(0))); //$NON-NLS-1$
        }
        nameValuePairs.add(new BasicNameValuePair("USERS",peopleString));//("USERS", "NaamaKeshet@gmail.comXXXtomer.keshet@gmail.comXXXa@b.comXXXg@c.com")); //$NON-NLS-1$
        return nameValuePairs;
    }

    private static InputStream requestToStream(HttpUriRequest hr, ConnectionManager arcm) throws ClientProtocolException, IOException{
        HttpResponse result = arcm.executeOnHttp(hr);
        InputStream is = result.getEntity().getContent();
        return is;
    }

    private static List<NameValuePair> createMailPostData(String mail){
        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
        nameValuePairs.add(new BasicNameValuePair("FRIEND",mail)); //$NON-NLS-1$
        return nameValuePairs;
    }

    //TODO IMPORTANT FUNCTION
    private static List<String[]> extractPropsArray(NodeList nodeLst,String[] props) {
        List<String[]> lfp = new ArrayList<String[]>();

        for (int s = 0; s < nodeLst.getLength(); s++) {

            Node fstNode = nodeLst.item(s);
            String[] arr = new String[props.length];

            if (fstNode.getNodeType() == Node.ELEMENT_NODE) {

                Element fstElmnt = (Element) fstNode;

                for (int i =0 ; i<props.length;i++){
                    NodeList fstNmElmntLst = fstElmnt.getElementsByTagName(props[i]);
                    Element fstNmElmnt = (Element) fstNmElmntLst.item(0);
                    NodeList fstNm = fstNmElmnt.getChildNodes();
                    arr[i] = (((Node) fstNm.item(0)).getNodeValue());
                }

                lfp.add(arr);
            }
        }

        return lfp;
    }

    public static List<FriendProps> requestPeople(FriendProps myFp,String people, ConnectionManager arcm) throws ClientProtocolException, IOException, ParserConfigurationException, SAXException{
        // sending current location and request for users
        HttpPost http_post = new HttpPost(AroundRoidAppConstants.gpsUrl);
        http_post.setEntity(new UrlEncodedFormEntity(createPostData(myFp,people)));
        InputStream is  = requestToStream(http_post,arcm);
        //data is recieved. starts parsing:
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(is);
        doc.getDocumentElement().normalize();
        NodeList nodeLst = doc.getElementsByTagName(FriendProps.root);
        List<String[]> propsArrList = extractPropsArray(nodeLst,FriendProps.props);
        List<FriendProps> fpl = FriendProps.fromArrList(propsArrList);
        //parsing complete!
        return fpl;

    }

    //TODO CHANGE
    public static boolean inviteMail(String people, ConnectionManager arcm) throws ClientProtocolException, IOException{
        // sending current location and request for users
        HttpPost http_post = new HttpPost(AroundRoidAppConstants.inviterUrl);
        http_post.setEntity(new UrlEncodedFormEntity(createMailPostData(people)));
        InputStream is  = requestToStream(http_post,arcm);
        byte[] buf = new byte[20];
        is.read(buf, 0, 4);
        return buf[0]=='s';
    }

    /*
    private static String convertStreamToString(InputStream is)

    throws IOException {
        /*
         * To convert the InputStream to String we use the
         * Reader.read(char[] buffer) method. We iterate until the
         * Reader return -1 which means there's no more data to
         * read. We use the StringWriter class to produce the string.
         * /
        if (is != null) {
            Writer writer = new StringWriter();
            char[] buffer = new char[1024];
            try {
                Reader reader = new BufferedReader(
                        new InputStreamReader(is, "UTF-8"));
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, n);
                }
            } finally {
                is.close();
            }
            return writer.toString();
        } else {
            return "";
        }
    }
    */



}

package com.aroundroidgroup.astrid.googleAccounts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/***
 * Contains information about someone's location.
 * intended to be used both for reading from cursor (without getting rowid and contact id)
 * and for reading from xml server response.
 * @author Tomer
 *
 */
public class FriendProps{

    public final static String root = "Friend"; //$NON-NLS-1$
    public final static String[] props = new String[]{"Latitude","Longtitude","Mail","Timestamp","Valid"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

    private String lat,lon;
    private String valid;
    private double dlat,dlon;
    private String mail;
    private long timestamp;
    private String time;

    public FriendProps() {
        //empty
    }

    public void loadArr (String[] arr){
        if (arr.length!=props.length){
            return;
        }
        setLat(arr[0]);
        setLon(arr[1]);
        setMail(arr[2]);
        setTime(arr[3]);
        setValid(arr[4]);
    }

    public static List<FriendProps> fromArrList(List<String[]> arrLst){
        List<FriendProps> fpl = new ArrayList<FriendProps>(arrLst.size());
        for(String[] arr : arrLst){
            FriendProps fp = new FriendProps();
            fp.loadArr(arr);
            fpl.add(fp);
        }
        return fpl;
    }

    private final static Comparator<FriendProps> mailComparator = new Comparator<FriendProps>(){
        @Override
        public int compare(FriendProps object1, FriendProps object2) {
            return object1.getMail().compareTo(object2.getMail());
        }
    };

    public static Comparator<FriendProps> getMailComparator(){
        return mailComparator;
    }

    public String getLat() {
        return lat;
    }

    public void setLat(String lat) {
        this.lat = lat;
        this.dlat = (Double.parseDouble(lat));
    }

    public String getLon() {
        return lon;
    }

    public void setLon(String lon) {
        this.lon = lon;
        this.dlon = (Double.parseDouble(lon));
    }

    public String getMail() {
        return mail;
    }

    public void setMail(String mail) {
        this.mail = mail;
    }

    public void setDlat(double dlat) {
        this.dlat = dlat;
        this.lat = String.valueOf(dlat);
    }

    public double getDlat() {
        return dlat;
    }

    public void setDlon(double dlon) {
        this.dlon = dlon;
        this.lon = String.valueOf(dlon);
    }

    public double getDlon() {
        return dlon;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        this.time = String.valueOf(timestamp);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTime(String time) {
        this.time = time;
        this.timestamp = Long.parseLong(time);
    }

    public String getTime() {
        return time;
    }

    public void setValid(String valid) {
        this.valid = valid;
    }

    public String getValid() {
        return valid;
    }

    public boolean isRegistered(){
        return getValid()!=null && getValid().compareTo(AroundRoidAppConstants.STATUS_UNREGISTERED)!=0;
    }

    public boolean isValid(){
        return getValid().compareTo(AroundRoidAppConstants.STATUS_ONLINE)==0;
    }

    @Override
    public String toString(){
        return getMail() + "::" + getLat() + "::" + getLon(); //$NON-NLS-1$ //$NON-NLS-2$
    }



}


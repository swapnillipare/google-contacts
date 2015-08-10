import java.util.List;
import java.util.ArrayList;

public class LdapContactBean{

  public static String defaultStr="not entered";
  public static String defaultFirstName="firstname";
  public static String defaultLastName="lastname";
  public static String DELIMITER="DELIMITER";
  public String fullname=defaultStr;
  public String firstname=defaultFirstName;
  public String lastname=defaultLastName;
  public String email=defaultStr;
  public String emailSecondary=defaultStr;

  //public List<String> workPhoneList = new ArrayList<String>();
//  public List<String> homePhoneList = new ArrayList<String>();
//  public List<String> mobilePhoneList = new ArrayList<String>();
//  public List<String> pagerPhoneList = new ArrayList<String>();
//  public List<String> faxPhoneList = new ArrayList<String>();

  public String workphone=defaultStr;
  public String homephone=defaultStr;
  public String fax=defaultStr;
  public String mobile=defaultStr;
  public String pager=defaultStr;

  public String streetWork=defaultStr;
  public String cityWork=defaultStr;
  public String stateWork=defaultStr;
  public String zipWork=defaultStr;
  public String streetHome=defaultStr;
  public String cityHome=defaultStr;
  public String stateHome=defaultStr;
  public String zipHome=defaultStr;
  public String company=defaultStr;
  public String title=defaultStr;
  public String notes=defaultStr;
  public String update=defaultStr;
  public String dept=defaultStr;
  public com.google.gdata.data.DateTime updateObj;

  public LdapContactBean(){
  }

  public static String getDelimited(List<String> list){
    if(list == null || list.size()==0){
      return defaultStr;
    }else{
      StringBuffer buffer = new StringBuffer();
      for(int i=0;i<list.size();++i){
        if (i>0) buffer.append(DELIMITER);
        buffer.append(list.get(i));
      }
      return buffer.toString();
    }
  }

  public static String[] getTokens(String raw){
    String[] strArr = raw.split(DELIMITER);
    return strArr;
  }

}

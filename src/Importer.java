import java.io.IOException;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.net.MalformedURLException;
import java.security.GeneralSecurityException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.auth.oauth2.Credential;
//import com.google.api.client.auth.oauth2.SingleUserCredentials;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.gdata.data.IContent;
import com.google.gdata.data.Content;
import com.google.gdata.data.DateTime;
import com.google.gdata.client.Query;
import com.google.gdata.data.contacts.UserDefinedField;
import com.google.gdata.client.contacts.ContactsService;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.contacts.ContactFeed;
import com.google.gdata.data.contacts.ContactGroupFeed;
import com.google.gdata.data.contacts.GroupMembershipInfo;
import com.google.gdata.data.contacts.ContactEntry;
import com.google.gdata.data.contacts.ContactGroupEntry;
import com.google.gdata.data.extensions.Email;
import com.google.gdata.data.extensions.Organization;
import com.google.gdata.data.extensions.OrgName;
import com.google.gdata.data.extensions.OrgTitle;
import com.google.gdata.data.extensions.PhoneNumber;
import com.google.gdata.data.extensions.ExtendedProperty;
import com.google.gdata.data.extensions.StructuredPostalAddress;
import com.google.gdata.data.extensions.Street;
import com.google.gdata.data.extensions.City;
import com.google.gdata.data.extensions.Region;
import com.google.gdata.data.extensions.PostCode;
import com.google.gdata.data.extensions.GivenName;
import com.google.gdata.data.extensions.FamilyName;
import com.google.gdata.data.extensions.Name;
import com.google.gdata.data.extensions.FullName;
import com.google.gdata.util.ServiceException;

public class Importer{

  public static String account=null;
  public static String user=null;
  public static String password=null;
  public static String infile=null;
  private final DateTime now = DateTime.now();
  private final String labelMyContacts = "System Group: My Contacts";
  private final String labelOtherContacts = "System Group: Other Contacts";
  private final String defaultVal= LdapContactBean.defaultStr;
  private final String defaultFirst= LdapContactBean.defaultFirstName;
  private final String defaultLast= LdapContactBean.defaultLastName;
  private final URL fullContactFeedURL; 
  private final URL fullGroupFeedURL;
  private URL myContactFeedURL; 
  private final ContactsService service;
  private PrintWriter addLdapWriter;
  private PrintWriter delLdapWriter;
  private PrintWriter modifyLdapWriter;
  private String atomMyContacts = null;
  private String atomOtherContacts = null;
  Map<String,LdapContactBean> ldapBeanMap;
  Map<String,String[]> pullOnlyMap;
  List<String> ldapNames;
  Set<String> googleNames;

  public static GoogleCredential createCredentialForServiceAccount(
    HttpTransport transport,
    JsonFactory jsonFactory,
    String serviceAccountId,
    Collection<String> serviceAccountScopes,
    File p12File) throws GeneralSecurityException, IOException {

    return new GoogleCredential.Builder().setTransport(transport)
        .setJsonFactory(jsonFactory)
        .setServiceAccountId(serviceAccountId)
        .setServiceAccountScopes(serviceAccountScopes)
        .setServiceAccountPrivateKeyFromP12File(p12File)
        .setServiceAccountUser(user)
        .build();
  }

  public Importer(String urlString,String groupUrlString)
  throws Exception{
    System.err.println("The time is now: "+now.toString());
    fullContactFeedURL = new URL(urlString); // to be used for queries
    fullGroupFeedURL = new URL(groupUrlString); // to be used for queries
    service = new ContactsService("LDAP insert");
    String clientId = "141885141025-r4jgisst56ue21i9ft1v63c7ak42uc74.apps.googleusercontent.com";
    String serviceEmail = "141885141025-r4jgisst56ue21i9ft1v63c7ak42uc74@developer.gserviceaccount.com";
    // Set up the HTTP transport and JSON factory
    HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    String accessScope ="https://www.google.com/m8/feeds"; 
    GoogleCredential gc = createCredentialForServiceAccount(httpTransport,jsonFactory,serviceEmail,Collections.singleton(accessScope),new File("m3teknik1-8d34c898e36a.p12"));
    if(!gc.refreshToken()){
      System.err.println("Can't refresh token");
      System.exit(1);
    }

    service.setOAuth2Credentials(gc);
    // the following method fails as it has been deprecated
    // service.setUserCredentials(user,password);
    // System.err.println("Created URL "+urlString+" and service");
    ldapBeanMap = new HashMap<String,LdapContactBean>();
    pullOnlyMap = new HashMap<String,String[]>();
    ldapNames = new ArrayList<String>();
    googleNames = new HashSet<String>();
    String header = "fullname	firstname	lastname	email	homephone	fax	mobile	pager	street	city	state	zip	workphone	company	title	notes	timestamp	dept";
    addLdapWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(account+".add"),"UTF-8"));
    delLdapWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(account+".del"),"UTF-8"));
    modifyLdapWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(account+".modify"),"UTF-8"));
    addLdapWriter.println(header);
    delLdapWriter.println(header);
    modifyLdapWriter.println(header);
    //ContactEntry newentry = new ContactEntry();
    //newentry.setTitle(new PlainTextConstruct("Tester2"));
    //newentry.setContent(new PlainTextConstruct("notes"));
    //Name name = new Name();
    //name.setFullName(new FullName("Tester4",null));
    //newentry.setName(name);
    //ContactEntry entry = service.insert(fullContactFeedURL,newentry); 
    //debugEntry(entry);
  }

  public void writeLine(PrintWriter writer, LdapContactBean lcb){
    String sep="\t";
    if (lcb.fullname.equals(defaultVal) ){
      String mesg = "Ignoring the LDAP insertion as full name is null";
      System.err.println(mesg);
      System.out.println(mesg);
    }else{
      String prefilter = 
      lcb.fullname+sep+lcb.firstname+sep+lcb.lastname+sep+
      lcb.email+sep+lcb.emailSecondary+sep+lcb.homephone+sep+lcb.fax+sep+
      lcb.mobile+sep+lcb.pager+sep+lcb.streetWork+sep+lcb.cityWork+sep+
      lcb.stateWork+sep+lcb.zipWork+sep+lcb.streetHome+sep+lcb.cityHome+
      sep+lcb.stateHome+sep+lcb.zipHome+sep+lcb.workphone+sep+
      lcb.company+sep+lcb.title+sep+lcb.notes+sep+lcb.update+sep+lcb.dept;
      String postfilter = prefilter.replace("\n"," ");
      //System.err.println("Prefilter: "+prefilter);    
      //System.err.println("Postfilter: "+postfilter);    
      writer.println(postfilter);
    }
  }

  public void parsePullOnly() throws Exception{
    String filename = "pull_only.txt";
    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename),"UTF-8"));
    String line;
    int lines = 0;
    System.err.println("Parsing pull only file: "+filename);
    reader.readLine();
    while((line=reader.readLine())!=null){
      LdapContactBean lcb = new LdapContactBean();
      String linearr[] = line.split("\t");
      int i = 0;
      String key = linearr[i++];
      String rawval = linearr[i++];
      String valarr[] = rawval.split(",");
      pullOnlyMap.put(key,valarr);
      //System.err.println("Inserted pull only key "+key);
      for(int j=0;j<valarr.length;++j){
        //System.err.println("Inserted pull only val "+valarr[j]);
      }
    }
  }

  public void parseLdapBean(String filename) throws Exception{
    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename),"UTF-8"));
    String line;
    int lines = 0;
    System.err.println("Parsing "+filename);
    line=reader.readLine();
    while((line=reader.readLine())!=null){
      LdapContactBean lcb = new LdapContactBean();
      String linearr[] = line.split("\t");
      int i = 0;
      String key = lcb.fullname = linearr[i++];
      lcb.firstname = linearr[i++];
      lcb.lastname = linearr[i++];
      lcb.email = linearr[i++];
      lcb.emailSecondary = linearr[i++];
      lcb.homephone = linearr[i++];
      lcb.fax = linearr[i++];
      lcb.mobile = linearr[i++];
      lcb.pager = linearr[i++];
      lcb.streetWork = linearr[i++];
      lcb.cityWork = linearr[i++];
      lcb.stateWork = linearr[i++];
      lcb.zipWork = linearr[i++];
      lcb.streetHome = linearr[i++];
      lcb.cityHome = linearr[i++];
      lcb.stateHome = linearr[i++];
      lcb.zipHome = linearr[i++];
      lcb.workphone = linearr[i++];
      lcb.company = linearr[i++];
      lcb.title = linearr[i++];
      lcb.notes = linearr[i++];
      lcb.update = linearr[i++];
      try{
        lcb.updateObj = DateTime.parseDateTime(lcb.update);
      }catch(java.lang.NumberFormatException ex){
        System.err.println("Couldn't parse "+lcb.update+" because "+ex.getMessage());
        //System.exit(1);
      }
      lcb.dept = linearr[i++];
      ldapBeanMap.put(key,lcb);
      //System.err.println("Loading key "+key);
      ldapNames.add(key);
      ++lines;
    }
    reader.close();
    System.err.println("Parsed "+lines+" contacts.");
  }

  private PhoneNumber setPhone(String rel,String number){
    PhoneNumber phone = new PhoneNumber();
    phone.setRel(rel);
    phone.setPhoneNumber(number);
    return phone;
  }

  private void addPhoneToContactEntry(ContactEntry entry,String rel,String ldapPhone){
    if (!ldapPhone.equals(defaultVal)){
      String[] tokens = LdapContactBean.getTokens(ldapPhone);
      for(int i=0;i<tokens.length;++i)
      entry.addPhoneNumber(setPhone(rel,tokens[i]));
    }
  }

  private ContactEntry copyFields(LdapContactBean lcb){
    ContactEntry entry  = new ContactEntry();
    setSynced(entry);
    entry.addGroupMembershipInfo(new GroupMembershipInfo(false,atomMyContacts));
    //entry.setTitle(new PlainTextConstruct(lcb.fullname));
    Name name = new Name();
    name.setFullName(new FullName(lcb.fullname,null));
    System.err.println("Setting title as "+lcb.fullname);
    entry.setName(name);
    if (!lcb.email.equals(defaultVal)){
      Email email = new Email();
      email.setAddress(lcb.email);
      email.setRel("http://schemas.google.com/g/2005#work");
      entry.addEmailAddress(email);
    }
    if (!lcb.emailSecondary.equals(defaultVal)){
      Email email = new Email();
      email.setAddress(lcb.emailSecondary);
      email.setRel("http://schemas.google.com/g/2005#home");
      entry.addEmailAddress(email);
    }
    addPhoneToContactEntry(entry,"http://schemas.google.com/g/2005#home",lcb.homephone);
    addPhoneToContactEntry(entry,"http://schemas.google.com/g/2005#mobile",lcb.mobile);
    addPhoneToContactEntry(entry,"http://schemas.google.com/g/2005#pager",lcb.pager);
    addPhoneToContactEntry(entry,"http://schemas.google.com/g/2005#home_fax",lcb.fax);
    addPhoneToContactEntry(entry,"http://schemas.google.com/g/2005#work",lcb.workphone);
    if (!lcb.company.equals(defaultVal) || !lcb.title.equals(defaultVal)){
      Organization org = new Organization();
      org.setOrgName(new OrgName(lcb.company));
      org.setOrgTitle(new OrgTitle(lcb.title));
      org.setRel("http://schemas.google.com/g/2005#work");
      entry.addOrganization(org);
    }
    if (!lcb.streetWork.equals(defaultVal)||!lcb.cityWork.equals(defaultVal)||!lcb.stateWork.equals(defaultVal)||!lcb.zipWork.equals(defaultVal)){
      StructuredPostalAddress strucAddr = new StructuredPostalAddress();
      strucAddr.setRel("http://schemas.google.com/g/2005#work");
      strucAddr.setStreet(new Street(lcb.streetWork));
      strucAddr.setCity(new City(lcb.cityWork));
      strucAddr.setRegion(new Region(lcb.stateWork));
      strucAddr.setPostcode(new PostCode(lcb.zipWork));
      entry.addStructuredPostalAddress(strucAddr);
    }
    if (!lcb.streetHome.equals(defaultVal)||!lcb.cityHome.equals(defaultVal)||!lcb.stateHome.equals(defaultVal)||!lcb.zipHome.equals(defaultVal)){
      StructuredPostalAddress strucAddr = new StructuredPostalAddress();
      strucAddr.setRel("http://schemas.google.com/g/2005#home");
      strucAddr.setStreet(new Street(lcb.streetHome));
      strucAddr.setCity(new City(lcb.cityHome));
      strucAddr.setRegion(new Region(lcb.stateHome));
      strucAddr.setPostcode(new PostCode(lcb.zipHome));
      entry.addStructuredPostalAddress(strucAddr);
    }
    if (!lcb.notes.equals(defaultVal)){
      entry.setContent(new PlainTextConstruct(lcb.notes));
    }
    if (!lcb.firstname.equals(defaultVal)){
      entry.getName().setGivenName(new GivenName(lcb.firstname,""));
    }
    if (!lcb.lastname.equals(defaultVal)){
      entry.getName().setFamilyName(new FamilyName(lcb.lastname,""));
    }
    return entry;
  }

  private String extractSubStr(Object obj){
    if (obj==null) return defaultVal;
    else{
      String addrStr = obj.toString();
      int first = addrStr.indexOf("value=")+6;
      int last = addrStr.indexOf("}");
      int len = last - first;
      String testStr = addrStr.substring(first,last);
      //System.err.println("Indices: "+first+","+len+","+testStr);
      return testStr;
    }
  }

  private int printDiff(String field, String ldap, String google){
    int diff =  !ldap.equals(google)?1:0;
    if (diff==1){
      String mesg = field+" differs. Google version: "+google+" LDAP version: "+ldap+".";
      System.err.println(mesg);
      System.out.println(user+": "+mesg);
    }else{
      String mesg = field+" The same. Google version: "+google+" LDAP version: "+ldap+".";
      //System.err.println(mesg);
    }
    return diff;
  }

  private int countDiff(ContactEntry entry,LdapContactBean lcb,LdapContactBean newLcb){
    int diff = 0;
    String fullname_google = entry.getTitle().getPlainText().length()==0?
    defaultVal : entry.getTitle().getPlainText();
   
    diff+=printDiff("Full name",lcb.fullname,fullname_google);
    newLcb.fullname = fullname_google;

    Name name = entry.getName();
    String firstName = defaultFirst;
    String lastName = defaultLast;
    if (name!=null){
      GivenName first = name.getGivenName();
      FamilyName last = name.getFamilyName();
      if (first!=null) firstName = first.getValue();
      if (last!=null) lastName = last.getValue();
    }
    diff+=printDiff("First name",lcb.firstname,firstName);
    newLcb.firstname = firstName;
    diff+=printDiff("Last name",lcb.lastname,lastName);
    newLcb.lastname = lastName;
    
    String notes = defaultVal;
    Content content = entry.getContent();
    if (content!=null){
      int contentType = content.getType();
        switch(contentType){
        case IContent.Type.TEXT:
          System.err.println("Text Notes found!");
          notes = entry.getPlainTextContent().length()==0?defaultVal:
          entry.getPlainTextContent();
          break;
      }
    }
    diff+=printDiff("Notes",lcb.notes,notes);
    newLcb.notes = notes;

    List<Organization> orgList = entry.getOrganizations();
    Iterator<Organization> itOrg = orgList.iterator();
    String company = defaultVal;
    String title = defaultVal;
    while(itOrg.hasNext()){
      Organization org = itOrg.next();
      //System.err.println("Found an org with namespace "+org.getRel());
      OrgName orgName = org.getOrgName();
      OrgTitle orgTitle = org.getOrgTitle();
      if (orgName!=null) company = orgName.getValue();
      if (orgTitle!=null) title = orgTitle.getValue();
    }
    diff+=printDiff("Company",lcb.company,company);
    newLcb.company = company;
    diff+=printDiff("Job Title",lcb.title,title);
    newLcb.title = title;
    
    String emailStr = defaultVal;
    String emailStrSecondary = defaultVal;
    List<Email> emailList = entry.getEmailAddresses();
    Iterator<Email> itEmail = emailList.iterator();
    while(itEmail.hasNext()){
      Email email = itEmail.next();
      if (emailStr.equals(defaultVal)){
        emailStr = email.getAddress();
      }else if (emailStrSecondary.equals(defaultVal)){
        emailStrSecondary = email.getAddress();
      }
//      if (email.getRel().equals("http://schemas.google.com/g/2005#work")){
//        emailStr = email.getAddress();
//      }else if (email.getRel().equals("http://schemas.google.com/g/2005#home")){
//        emailStrSecondary = email.getAddress();
//      }
    }
    diff+=printDiff("Email", lcb.email,emailStr);
    newLcb.email = emailStr;
    diff+=printDiff("Email Secondary", lcb.emailSecondary,emailStrSecondary);
    newLcb.emailSecondary = emailStrSecondary;
    // PHONE NUMBERS
    List<PhoneNumber> phoneList = entry.getPhoneNumbers();
    List<String> homephone = new ArrayList<String>();
    List<String> workphone = new ArrayList<String>();
    List<String> cellphone = new ArrayList<String>();
    List<String> pager = new ArrayList<String>();
    List<String> fax = new ArrayList<String>();
    Iterator<PhoneNumber> itPhone = phoneList.iterator();
    while(itPhone.hasNext()){
      PhoneNumber phoneNo = itPhone.next();
      String rel = phoneNo.getRel();
      if (rel!=null){
        if (rel.equals("http://schemas.google.com/g/2005#home")){
          homephone.add(phoneNo.getPhoneNumber());
        }else if (rel.equals("http://schemas.google.com/g/2005#mobile")){
          cellphone.add(phoneNo.getPhoneNumber());
        }else if (rel.equals("http://schemas.google.com/g/2005#pager")){
          pager.add(phoneNo.getPhoneNumber());
        
        }else if (rel.equals("http://schemas.google.com/g/2005#home_fax")){
          fax.add(phoneNo.getPhoneNumber());
        }else if (rel.equals("http://schemas.google.com/g/2005#work")){
          workphone.add(phoneNo.getPhoneNumber());
        }
      }
    }
    diff+=printDiff("Home phone", lcb.homephone,lcb.getDelimited(homephone));
    newLcb.homephone = lcb.getDelimited(homephone);
    diff+=printDiff("Cell phone", lcb.mobile,lcb.getDelimited(cellphone));
    newLcb.mobile = lcb.getDelimited(cellphone);
    diff+=printDiff("Pager", lcb.pager,lcb.getDelimited(pager));
    newLcb.pager = lcb.getDelimited(pager);
    diff+=printDiff("Fax", lcb.fax,lcb.getDelimited(fax));
    newLcb.fax = lcb.getDelimited(fax);
    diff+=printDiff("Work phone", lcb.workphone,lcb.getDelimited(workphone));
    newLcb.workphone = lcb.getDelimited(workphone);
    // MAILING ADDRESSES
    String streetStrWork = defaultVal;
    String cityStrWork = defaultVal;
    String regionStrWork = defaultVal;
    String zipStrWork = defaultVal;
    String streetStrHome = defaultVal;
    String cityStrHome = defaultVal;
    String regionStrHome = defaultVal;
    String zipStrHome = defaultVal;
    List<StructuredPostalAddress> addrList = entry.getStructuredPostalAddresses();
    Iterator<StructuredPostalAddress> itAddr = addrList.iterator();
    while(itAddr.hasNext()){
      StructuredPostalAddress addr = itAddr.next();
      if (addr.getRel().equals("http://schemas.google.com/g/2005#work")){
        streetStrWork = extractSubStr(addr.getStreet()); 
        cityStrWork = extractSubStr(addr.getCity()); 
        regionStrWork = extractSubStr(addr.getRegion()); 
        zipStrWork = extractSubStr(addr.getPostcode()); 
      }else if (addr.getRel().equals("http://schemas.google.com/g/2005#home")){
        streetStrHome = extractSubStr(addr.getStreet()); 
        cityStrHome = extractSubStr(addr.getCity()); 
        regionStrHome = extractSubStr(addr.getRegion()); 
        zipStrHome = extractSubStr(addr.getPostcode()); 
     }
    }
    diff+=printDiff("Work Street address", lcb.streetWork,streetStrWork);
    newLcb.streetWork = streetStrWork;
    diff+=printDiff("Work City", lcb.cityWork,cityStrWork);
    newLcb.cityWork = cityStrWork;
    diff+=printDiff("Work State", lcb.stateWork,regionStrWork);
    newLcb.stateWork = regionStrWork;
    diff+=printDiff("Work Zip code", lcb.zipWork,zipStrWork);
    newLcb.zipWork = zipStrWork;
    diff+=printDiff("Home Street address", lcb.streetHome,streetStrHome);
    newLcb.streetHome = streetStrHome;
    diff+=printDiff("Home City", lcb.cityHome,cityStrHome);
    newLcb.cityHome = cityStrHome;
    diff+=printDiff("Home State", lcb.stateHome,regionStrHome);
    newLcb.stateHome = regionStrHome;
    diff+=printDiff("Home Zip code", lcb.zipHome,zipStrHome);
    newLcb.zipHome = zipStrHome;
    System.err.println("Total diff: "+diff);
    return diff;
  }

  private void debugEntry(ContactEntry entry){
    String fullName = entry.getTitle().getPlainText();
    System.err.println("Inserted title: "+fullName);
    List<Email> emailList = entry.getEmailAddresses();
    Iterator<Email> itEmail = emailList.iterator();
    while(itEmail.hasNext()){
      Email phoneNo = itEmail.next();
      System.err.println("Email "+phoneNo.getRel()+": "+phoneNo.getAddress());
    }
    List<PhoneNumber> phoneList = entry.getPhoneNumbers();
    Iterator<PhoneNumber> itPhone = phoneList.iterator();
    while(itPhone.hasNext()){
      PhoneNumber phoneNo = itPhone.next();
      System.err.println("Phone "+phoneNo.getRel()+": "+phoneNo.getPhoneNumber());
    }
    List<StructuredPostalAddress> addrList = entry.getStructuredPostalAddresses();
    System.err.println("This contact has "+addrList.size()+" addresses.");
    Iterator<StructuredPostalAddress> itAddr = addrList.iterator();
    while(itAddr.hasNext()){
      StructuredPostalAddress addr = itAddr.next();
      System.err.println("Address "+addr.getRel()+": "+addr.getStreet()+","+addr.getCity()+","+addr.getRegion()+","+addr.getPostcode());
    }
  }

  private boolean isSynced(ContactEntry entry){
    boolean is_sync = false; 
    UserDefinedField query = new UserDefinedField("ldap","sync");
    if (entry.hasUserDefinedFields()){
      Iterator<UserDefinedField> it = entry.getUserDefinedFields().iterator();
      while(it.hasNext()){
        UserDefinedField userField = (UserDefinedField)it.next();
        if (userField.equals(query)) is_sync = true;
      }
    }
    return is_sync;
  }

  private void setSynced(ContactEntry entry){
    if (!isSynced(entry)){
      System.err.println("Was not synced");
      UserDefinedField query = new UserDefinedField("ldap","sync");
      entry.addUserDefinedField(query);
    }
    if (isSynced(entry)){
      //System.err.println("Sanity check passed!");
    }
  }

  public void replaceAll() throws Exception{
    Query query = new Query(fullContactFeedURL);
    query.setMaxResults(10000);
    ContactFeed resultFeed = service.query(query, ContactFeed.class);
    // Print the results
    System.err.println(resultFeed.getTitle().getPlainText());
    Set<String> insertedNames = new HashSet<String>();
    for (int i = 0; i < resultFeed.getEntries().size(); i++) {
      ContactEntry oldEntry = resultFeed.getEntries().get(i);
      String fullName = oldEntry.getTitle().getPlainText();
      boolean inMyContacts = false;
      Iterator<GroupMembershipInfo> it = oldEntry.getGroupMembershipInfos().iterator();
      while(it.hasNext()){
        GroupMembershipInfo group = (GroupMembershipInfo)it.next();
        if(atomMyContacts.equals(group.getHref())){
          //System.err.println("Is in My Contacts");
          inMyContacts = true;
        }
      }
      if (inMyContacts){
        System.err.println("Processing Contact Title: "+fullName);
        DateTime updatedTime = oldEntry.getUpdated();
        //if (updatedTime!=null){
          //System.err.println("Debug: Last update: "+updatedTime.toString()+","+updatedTime.toStringRfc822()+","+updatedTime.toUiString());
        //}
        if (insertedNames.contains(fullName)){
          System.err.println("Deleting duplicate from Google.");
          oldEntry.delete();
        }else{
          // keep track of what Google has so we can insert later if we have
          // new contacts that LDAP is missing
          googleNames.add(fullName); 
          //System.err.println("Added google name *"+fullName+"*");
          LdapContactBean lcb = ldapBeanMap.get(fullName);
          LdapContactBean newLCB = new LdapContactBean(); 
          if (lcb==null) {
            LdapContactBean emptyLCB = new LdapContactBean(); 
            newLCB.dept = account;
            int diff = countDiff(oldEntry,emptyLCB,newLCB);
            System.err.println("LDAP Map is null for "+fullName+". Determining whether to add to LDAP or delete from Google.");
            long hours = (now.getValue()-updatedTime.getValue())/(3600*1000);
            System.err.println("Difference in hours: "+hours);
            boolean synced = isSynced(oldEntry);
            //if (hours>1){
            if (synced){
              System.out.println(user+": De-subscribing "+fullName+" from Google.");
              oldEntry.delete();
            }else{
              System.out.println(user+": Adding "+fullName+" into LDAP from Google");
              writeLine(addLdapWriter,newLCB);
            }
          }else{
            newLCB.dept = lcb.dept;
            if (updatedTime!=null){
              System.err.println("Google last update: "+updatedTime.toString());
              System.err.println("LDAP last update: "+lcb.updateObj.toString());
              long hours = (now.getValue()-updatedTime.getValue())/(3600*1000);
            }
            int diff = countDiff(oldEntry,lcb,newLCB);
            if (diff==0){
              System.err.println("No change. Record is identical.");
            }else{
              if (updatedTime.compareTo(lcb.updateObj)>0){
                // check the pull only map to make sure this contact isn't
                // blacklisted
                boolean replaceLdap = true;
                //System.err.println("Check pull only for "+fullName);
                String [] account_arr = pullOnlyMap.get(fullName);
                if (account_arr!=null){
                  for(int j=0;j<account_arr.length;++j){
                    //System.err.println("Checking for match between "+account+" and "+account_arr[j]);
                    if (account.equals(account_arr[j])){
                      replaceLdap = false;
                      System.err.println(account+" prohibited from pushing changes on "+fullName+" to LDAP");
                      System.err.println("Replacing "+fullName+" in Google with info from LDAP");
                      oldEntry.delete();
                      ContactEntry newentry = new ContactEntry();
                      newentry.setTitle(new PlainTextConstruct(lcb.fullname));
                      ContactEntry addedEntry = service.insert(fullContactFeedURL,copyFields(lcb)); 
                      debugEntry(addedEntry);
                      System.out.println(user+": Account "+account+" is prohibited from pushing changes on "+fullName+" to LDAP");
                      System.out.println(user+": Replaced "+fullName+" in Google with info from LDAP");
                    }
                  }
                }
                if (replaceLdap){
                  System.err.println("Google contact is newer");
                  writeLine(modifyLdapWriter,newLCB);
                  System.out.println(user+": Replacing "+fullName+" in LDAP with info from Google");
                }
              }else{
                System.err.println("LDAP contact is newer");
                System.err.println("Replacing "+fullName+" in Google with info from LDAP");
                oldEntry.delete();
                ContactEntry newentry = new ContactEntry();
                newentry.setTitle(new PlainTextConstruct(lcb.fullname));
                ContactEntry addedEntry = service.insert(fullContactFeedURL,copyFields(lcb)); 
                debugEntry(addedEntry);
                System.out.println(user+": Replaced "+fullName+" in Google with info from LDAP");
              }
            }
          }
          insertedNames.add(fullName);
        }
        if(!isSynced(oldEntry)){
          setSynced(oldEntry);
          try{
            ContactEntry updatedEntry = service.update(new URL(oldEntry.getEditLink().getHref()),oldEntry); 
            System.err.println("Updated entry to sync status true");
            debugEntry(updatedEntry);
          }catch(com.google.gdata.util.ResourceNotFoundException ex){
            System.err.println("Error: Failed to updated entry to sync status to true because "+ex.getMessage());
          }
            
        }
      }else{
        System.err.println("Skipping Contact Title: "+fullName);
      }
    }
    addLdapWriter.close();
    modifyLdapWriter.close();
  }

  public void addAll() throws Exception{
    Iterator<String> ldapIt = ldapNames.iterator();
    int sleepinterval = 1000;
    while(ldapIt.hasNext()){
      String fullName1 = ldapIt.next();
      //System.err.println("Looking for google name *"+fullName1+"*");
      if (googleNames==null) {
         System.err.println("Shouldn't happen that google names is null");
         System.exit(1);
      }
      if (!googleNames.contains(fullName1)){
        LdapContactBean lcb = ldapBeanMap.get(fullName1);
        if (lcb!=null){
          ContactEntry entryToAdd = copyFields(lcb);
          DateTime updatedTime = entryToAdd.getUpdated();
          long hours = (now.getValue()-lcb.updateObj.getValue())/(3600*1000);
          System.err.println("Difference in hours: "+hours);
          if (lcb.dept.equals(account) && hours>1){
            //delete from LDAP
            System.out.println(user+": Deleting "+fullName1+" from LDAP");
            writeLine(delLdapWriter,lcb);
          }else{
            System.err.println("Adding new contact found only in LDAP");
            ContactEntry addedEntry = service.insert(fullContactFeedURL,entryToAdd); 
            debugEntry(addedEntry);
            Thread.sleep(sleepinterval);
            System.out.println(user+": Adding into Google new contact "+fullName1+
            " found only in LDAP");
          }
        }
      }
    }
    delLdapWriter.close();
  }

public void setMyContacts()
    throws ServiceException, IOException {
    // Request the feed
    ContactGroupFeed resultFeed = service.getFeed(fullGroupFeedURL, ContactGroupFeed.class);

    for (ContactGroupEntry groupEntry : resultFeed.getEntries()) {
      System.err.println("Atom Id: " + groupEntry.getId());
      System.err.println("Group Name: " + groupEntry.getTitle().getPlainText());
      //System.err.println("Last Updated: " + groupEntry.getUpdated());
      if (labelMyContacts.equals(groupEntry.getTitle().getPlainText())){
        atomMyContacts = groupEntry.getId();
        myContactFeedURL = new URL(atomMyContacts); 
        System.err.println("Found My Contacts label, setting atom ID: "+atomMyContacts);
      }else if (labelOtherContacts.equals(groupEntry.getTitle().getPlainText())){
        atomOtherContacts = groupEntry.getId();
        System.err.println("Found Other Contacts label, setting atom ID: "+atomMyContacts);
      }
      for (ExtendedProperty property : groupEntry.getExtendedProperties()) {
        if (property.getValue() != null) {
          System.err.println("  " + property.getName() + "(value) = " +
              property.getValue());
         } else if (property.getXmlBlob() != null) {
          System.err.println("  " + property.getName() + "(xmlBlob) = " +
              property.getXmlBlob().getBlob());
        }
      }
      //System.err.println("Self Link: " + groupEntry.getSelfLink().getHref());
      if (!groupEntry.hasSystemGroup()) {
        // System groups do not have an edit link
        //System.err.println("Edit Link: " + groupEntry.getEditLink().getHref());
        //System.err.println("ETag: " + groupEntry.getEtag());
      }
      if (groupEntry.hasSystemGroup()) {
        //System.err.println("System Group Id: " +
        groupEntry.getSystemGroup().getId();
      }
    }
  }


  public static void main(String args[]){
    try{
      //System.err.println("Command line arguments: "+args.length);
      if (args.length<4){
        System.err.println("Usage: <linux acct> <user login> <password> <ldap_infile>");
        return;
      }
      int arg = 0;
      account = args[arg++];
      user = args[arg++];
      password = args[arg++];
      infile = args[arg++];
      String urlString = "https://www.google.com/m8/feeds/contacts/"+user+"/full";
      String groupUrlString = "https://www.google.com/m8/feeds/groups/"+user+"/full";
      Importer importer = new Importer(urlString,groupUrlString);
      importer.parsePullOnly();
      importer.setMyContacts();
      importer.parseLdapBean(infile);
      importer.replaceAll();
      importer.addAll();
    }catch (Exception ex){
      System.err.println("Exception caught with message "+ex.getMessage());
      ex.printStackTrace();
    }
  }
}

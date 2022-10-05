package com.databricks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;


public class AadSyncApplication {

    private static String authority;
    private static String clientId;
    private static String secret;
    private static String scope;

    private static String dbscimToken;

    private static String dbbaseUrl;

    private static ConfidentialClientApplication app;

    static final int  TIME_OUT = 1;

    private static final Logger log = LoggerFactory.getLogger(AadSyncApplication.class);

    private static final HashMap<User, Set<String>> userGroupmap = new HashMap<User, Set<String>>();

    private static final HashMap<String, Set<User>> groupUsermap = new HashMap<String, Set<User>>();

    private static boolean dryRun = false;



    public static void main(String args[]) throws Exception {

        setUpSampleData();

        try {

            for (String arg : args) {
                log.info("Program arguments are");
                log.info(arg);
                if (arg.equals("--dryRun"))
                    dryRun = true;
            }
            BuildConfidentialClientObject();
            IAuthenticationResult result = getAccessTokenByClientCredentialGrant();
            //String usersListFromGraph1 = getUsersListFromGraph1(result.accessToken());

            //System.out.println("Users in the Tenant = " + usersListFromGraph1);

            //Contains all members
            Users usersListFromGraph = getUsersListFromGraph(result.accessToken());

            //Contains all groups including nested groups
            Groups groupsListFromGraph = getGroupsListFromGraph(result.accessToken());

            log.info("--------AAD read DONE--------");


            //Iterate each group
            for (Group g : groupsListFromGraph.value) {

                for (String arg : args) {
                    if (!arg.startsWith("--") && arg.equalsIgnoreCase(g.displayName))
                        extractFromGroup(result, g.id, g.displayName);
                }

            }

            DBUsersResp dbusExisting=getDBUsers();

            //Iterate through Users
            for (User key : userGroupmap.keySet()) {

               log.info("Iterate and SYNC Users");
                //create all users
                if(!dryRun)
                {
                    DBUser dbuser=new DBUser(key.mail,key.displayName);

                    boolean exists=false;
                    for(DBUserResp ue :dbusExisting.resources)
                    {
                        if(ue.displayName.equalsIgnoreCase(dbuser.displayName))
                            exists=true;

                    }
                    if(!exists)
                        createDBUser(dbuser);
                }

            }
            //DB Users list
            DBUsersResp dbus=getDBUsers();



            //Iterate through groups
            for (String key : groupUsermap.keySet()) {

                DBGroup g=new DBGroup();
                g.displayName=key;
                g.schemas=Arrays.asList("urn:ietf:params:scim:schemas:core:2.0:Group");
                List<DBGroupMember> members=new ArrayList<DBGroupMember>();

                //iterate users in the list
                //we are expecting that all users are added in previous step
                //so we will get id and add to members
                for (User value : groupUsermap.get(key)) {
                    log.info("-----Analysing mappings------");
                    log.info("Group :" + key + " User :" + value);
                    if (!dryRun) {
                        String uid="";
                        for(DBUserResp dbu : dbus.resources)
                        {
                            if(dbu.displayName.equals(value.displayName))
                                members.add(new DBGroupMember(dbu.id));
                        }

                    }
                }

                g.members=members;
                createDBGroup(g);

            }

            System.out.println("Press any key to exit ...");
            System.in.read();


        } catch (Exception ex) {
            System.out.println("Oops! We have an exception of type - " + ex.getClass());
            System.out.println("Exception message - " + ex.getMessage());
            throw ex;
        }
    }

    private static void extractFromGroup(IAuthenticationResult result, String gid, String displayName) throws IOException {
        //Get Membership per group
        GroupMembers groupMembers = getGroupsMembersFromGraph(result.accessToken(), gid);

        //Iterate each members
        for (GroupMember gm : groupMembers.value) {
            if (gm.odataType.equals("#microsoft.graph.user")) {
                //log.info("User :" + gm.displayName + "----Group :" + displayName);

                for (String gp : displayName.split(":")) {
                    Set<User> users = groupUsermap.get(gp);
                    if (users != null) {
                        users.add(new User(gm.displayName, (String) gm.userPrincipalName));
                    } else {
                        groupUsermap.put(gp, new HashSet<>(Arrays.asList(new User(gm.displayName, (String) gm.userPrincipalName))));
                    }

                }


                Set<String> groups = userGroupmap.get(new User(gm.displayName, (String) gm.userPrincipalName));
                if (groups != null) {
                    for (String a : displayName.split(":"))
                        groups.add(a);
                } else
                    userGroupmap.put(new User(gm.displayName, (String) gm.userPrincipalName), (new HashSet<>(Arrays.asList(displayName.split(":")))));


            } else if (gm.odataType.equals("#microsoft.graph.group")) {
                extractFromGroup(result, gm.id, displayName + ":" + gm.displayName);

            }

        }


    }

    private static DBUsersResp getDBUsers() throws IOException {

        DBUsersResp resp = null;
        String apiUrl = dbbaseUrl+"/Users";

        HttpURLConnection conn = getHttpURLConnection(dbscimToken, apiUrl);

        int httpResponseCode = conn.getResponseCode();
        if (httpResponseCode == HTTPResponse.SC_OK) {

            StringBuilder response;
            String fullJson = getStringBuilder(conn);

            ObjectMapper mapper = new ObjectMapper();

            try {


                resp = mapper.readValue(fullJson, DBUsersResp.class);


            } catch (IOException e) {

                e.printStackTrace();
            }

        }
        return resp;
    }

    private static GroupMembers getGroupsMembersFromGraph(String accessToken, String id) throws IOException {

        GroupMembers resp = null;
        String apiUrl = "https://graph.microsoft.com/v1.0/groups/" + id + "/members";

        HttpURLConnection conn = getHttpURLConnection(accessToken, apiUrl);

        int httpResponseCode = conn.getResponseCode();
        if (httpResponseCode == HTTPResponse.SC_OK) {

            StringBuilder response;
            String fullJson = getStringBuilder(conn);

            ObjectMapper mapper = new ObjectMapper();

            try {


                resp = mapper.readValue(fullJson, GroupMembers.class);


            } catch (IOException e) {

                e.printStackTrace();
            }

        }
        return resp;
    }

    private static Groups getGroupsListFromGraph(String accessToken) throws IOException {
        Groups resp = null;
        String apiUrl = "https://graph.microsoft.com/v1.0/groups";

        HttpURLConnection conn = getHttpURLConnection(accessToken, apiUrl);

        int httpResponseCode = conn.getResponseCode();
        if (httpResponseCode == HTTPResponse.SC_OK) {

            StringBuilder response;
            String fullJson = getStringBuilder(conn);

            ObjectMapper mapper = new ObjectMapper();

            try {


                resp = mapper.readValue(fullJson, Groups.class);


            } catch (IOException e) {

                e.printStackTrace();
            }

        }
        return resp;
    }

    private static void BuildConfidentialClientObject() throws Exception {

        // Load properties file and set properties used throughout the sample
        app = ConfidentialClientApplication.builder(
                        clientId,
                        ClientCredentialFactory.createFromSecret(secret))
                .authority(authority)
                .build();
    }

    private static IAuthenticationResult getAccessTokenByClientCredentialGrant() throws Exception {

        // With client credentials flows the scope is ALWAYS of the shape "resource/.default", as the
        // application permissions need to be set statically (in the portal), and then granted by a tenant administrator
        ClientCredentialParameters clientCredentialParam = ClientCredentialParameters.builder(
                        Collections.singleton(scope))
                .build();

        CompletableFuture<IAuthenticationResult> future = app.acquireToken(clientCredentialParam);
        return future.get();
    }

    private static Users getUsersListFromGraph(String accessToken) throws IOException {
        Users resp = null;
        String apiUrl = "https://graph.microsoft.com/v1.0/users";

        HttpURLConnection conn = getHttpURLConnection(accessToken, apiUrl);

        int httpResponseCode = conn.getResponseCode();
        if (httpResponseCode == HTTPResponse.SC_OK) {

            StringBuilder response;
            String fullJson = getStringBuilder(conn);

            ObjectMapper mapper = new ObjectMapper();

            try {

                resp = mapper.readValue(fullJson, Users.class);


            } catch (IOException e) {

                e.printStackTrace();
            }

        }
        return resp;
    }

    private static String addUser(String accessToken) throws IOException {
        URL url = new URL("https://graph.microsoft.com/v1.0/groups/1a11e40f-fbde-4b38-b046-e9e6b5619e40");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept", "application/json");

        int httpResponseCode = conn.getResponseCode();
        if (httpResponseCode == HTTPResponse.SC_OK) {

            StringBuilder response;
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {

                String inputLine;
                response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            }
            return response.toString();
        } else {
            return String.format("Connection returned HTTP code: %s with message: %s",
                    httpResponseCode, conn.getResponseMessage());
        }
    }

    private static String getUsersListFromGraph1(String accessToken) throws IOException {
        URL url = new URL("https://graph.microsoft.com/v1.0/groups/1a11e40f-fbde-4b38-b046-e9e6b5619e40");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept", "application/json");

        int httpResponseCode = conn.getResponseCode();
        if (httpResponseCode == HTTPResponse.SC_OK) {

            StringBuilder response;
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {

                String inputLine;
                response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            }
            return response.toString();
        } else {
            return String.format("Connection returned HTTP code: %s with message: %s",
                    httpResponseCode, conn.getResponseMessage());
        }
    }


    private static String getStringBuilder(HttpURLConnection conn) throws IOException {
        StringBuilder response;
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {

            String inputLine;
            response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
        }
        return response.toString();
    }

    private static HttpURLConnection getHttpURLConnection(String accessToken, String apiUrl) throws IOException {
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept", "application/json");
        return conn;
    }

    /**
     * Helper function unique to this sample setting. In a real application these wouldn't be so hardcoded, for example
     * different users may need different authority endpoints or scopes
     */
    private static void setUpSampleData() throws IOException {
        // Load properties file and set properties used throughout the sample
        Properties properties = new Properties();
        properties.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("application.properties"));
        authority = properties.getProperty("AUTHORITY");
        clientId = properties.getProperty("CLIENT_ID");
        secret = properties.getProperty("SECRET");
        scope = properties.getProperty("SCOPE");
        dbscimToken = properties.getProperty("SCIM_TOKEN");
        dbbaseUrl = properties.getProperty("BASE_URL");
    }

    private static String createDBUser(DBUser u) throws IOException, InterruptedException {
        log.info("Attempting to Creating user :"+u.displayName);
        Thread.sleep(2000);
        URL url = new URL(dbbaseUrl + "/Users");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + dbscimToken);
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        ObjectMapper mapper = new ObjectMapper();
        String jsonInString = mapper.writeValueAsString(u);
        byte[] out = jsonInString.getBytes(StandardCharsets.UTF_8);
        int length = out.length;

        conn.setFixedLengthStreamingMode(length);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.connect();
        try (OutputStream os = conn.getOutputStream()) {
            os.write(out);
            log.info("User creation success for :"+u.displayName);
        }
        catch(Exception ex) {
            log.info("User creation fail for :"+u.displayName);

        }

        return "";


    }

    private static String createDBGroup(DBGroup u) throws IOException, InterruptedException {

        log.info("Attempting to Creating group :"+u.displayName);

        Thread.sleep(TIME_OUT *1000);
        URL url = new URL(dbbaseUrl + "/Groups");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + dbscimToken);
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        ObjectMapper mapper = new ObjectMapper();
        String jsonInString = mapper.writeValueAsString(u);
        byte[] out = jsonInString.getBytes(StandardCharsets.UTF_8);
        int length = out.length;

        conn.setFixedLengthStreamingMode(length);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.connect();
        try (OutputStream os = conn.getOutputStream()) {
            os.write(out);
            log.info("Group creation success for :"+u.displayName);
        }
        catch (Exception ex)
        {
            log.info("Group creation failed for :"+u.displayName);
        }

        return "";


    }

    private static void testPostCall()
    {
//        CloseableHttpClient client = HttpClients.createDefault();
//        HttpPost httpPost = new HttpPost("http://www.example.com");
//
//        String json = "{"id":1,"name":"John"}";
//        StringEntity entity = new StringEntity(json);
//        httpPost.setEntity(entity);
//        httpPost.setHeader("Accept", "application/json");
//        httpPost.setHeader("Content-type", "application/json");
//
//        CloseableHttpResponse response = client.execute(httpPost);
//        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
//        client.close();
//        return "";
    }
}
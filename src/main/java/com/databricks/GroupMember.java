package com.databricks;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Date;

public class GroupMember {

    @JsonProperty("@odata.type")
    public String odataType;
    public String id;
    public ArrayList<Object> businessPhones;
    public String displayName;
    public String givenName;
    public Object jobTitle;
    public Object mail;
    public Object mobilePhone;
    public Object officeLocation;
    public Object preferredLanguage;
    public String surname;
    public String userPrincipalName;
    public Object deletedDateTime;
    public Object classification;
    public Date createdDateTime;
    public ArrayList<Object> creationOptions;
    public Object description;
    public Object expirationDateTime;
    public ArrayList<Object> groupTypes;
    public Object isAssignableToRole;
    public boolean mailEnabled;
    public String mailNickname;
    public Object membershipRule;
    public Object membershipRuleProcessingState;
    public Object onPremisesDomainName;
    public Object onPremisesLastSyncDateTime;
    public Object onPremisesNetBiosName;
    public Object onPremisesSamAccountName;
    public Object onPremisesSecurityIdentifier;
    public Object onPremisesSyncEnabled;
    public Object preferredDataLocation;
    public ArrayList<Object> proxyAddresses;
    public Date renewedDateTime;
    public ArrayList<Object> resourceBehaviorOptions;
    public ArrayList<Object> resourceProvisioningOptions;
    public boolean securityEnabled;
    public String securityIdentifier;
    public Object theme;
    public Object visibility;
    public ArrayList<Object> onPremisesProvisioningErrors;

}

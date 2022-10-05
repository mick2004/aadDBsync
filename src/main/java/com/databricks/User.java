package com.databricks;

import java.util.ArrayList;
import java.util.Objects;

public class User {
    public User() {
    }

    public ArrayList<Object> businessPhones;
    public String displayName;
    public String givenName;
    public Object jobTitle;

    @Override
    public String toString() {
        return "User{" +
                "displayName='" + displayName + '\'' +

                ", mail='" + mail + '\'' +
                '}';
    }

    public String mail;
    public Object mobilePhone;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return displayName.equals(user.displayName) && mail.equals(user.mail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayName, mail);
    }

    public Object officeLocation;
    public Object preferredLanguage;
    public String surname;
    public String userPrincipalName;
    public String id;

    public User(String displayName, String mail) {
        this.displayName = displayName;

        this.mail = mail;
    }
}

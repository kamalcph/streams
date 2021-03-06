package my.examples.streams.pojo;

import java.io.Serializable;

public class UserProfile implements Serializable {

    private String experience;
    private String region;

    public UserProfile(String experience, String region) {
        this.experience = experience;
        this.region = region;
    }

    public String getExperience() {
        return experience;
    }

    public void setExperience(String experience) {
        this.experience = experience;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    @Override
    public String toString() {
        return "UserProfile{" +
                "experience='" + experience + '\'' +
                ", region='" + region + '\'' +
                '}';
    }
}

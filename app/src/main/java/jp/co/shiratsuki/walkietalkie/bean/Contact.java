package jp.co.shiratsuki.walkietalkie.bean;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 联系人实体类
 * Created at 2019/1/19 13:16
 *
 * @author Li Yuliang
 * @version 1.0
 */

public class Contact implements Parcelable {

    private String userId;
    private String userName;
    private String company;
    private String department;
    private String iconUrl;
    private String roomId;
    private String roomName;
    private boolean inRoom;
    private boolean speaking;

    public Contact(String userId, String userName, String company, String department, String iconUrl, String roomId,
                   String roomName, boolean inRoom, boolean speaking) {
        super();
        this.userId = userId;
        this.userName = userName;
        this.company = company;
        this.department = department;
        this.iconUrl = iconUrl;
        this.roomId = roomId;
        this.roomName = roomName;
        this.inRoom = inRoom;
        this.speaking = speaking;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public boolean isInRoom() {
        return inRoom;
    }

    public void setInRoom(boolean inRoom) {
        this.inRoom = inRoom;
    }

    public boolean isSpeaking() {
        return speaking;
    }

    public void setSpeaking(boolean speaking) {
        this.speaking = speaking;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(userId);
        dest.writeString(userName);
        dest.writeString(company);
        dest.writeString(department);
        dest.writeString(iconUrl);
        dest.writeString(roomId);
        dest.writeString(roomName);
        dest.writeByte((byte) (inRoom ? 1 : 0));
        dest.writeByte((byte) (speaking ? 1 : 0));
    }

    public static final Parcelable.Creator<Contact> CREATOR = new Parcelable.Creator<Contact>() {
        @Override
        public Contact createFromParcel(Parcel source) {
            return new Contact(source.readString(), source.readString(), source.readString(), source.readString(), source.readString(),
                    source.readString(), source.readString(), source.readByte() != 0, source.readByte() != 0);
        }

        @Override
        public Contact[] newArray(int size) {
            return new Contact[size];
        }
    };
}

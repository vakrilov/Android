package org.helpapaw.helpapaw.data.models.backendless;

import com.backendless.BackendlessUser;

/**
 * Created by iliyan on 8/4/16
 */
public class Comment {
    private String objectId;
    private String text;
    private String created;
    private BackendlessUser author;

    public Comment(String objectId, String text, String created, BackendlessUser author) {
        this.objectId = objectId;
        this.text = text;
        this.created = created;
        this.author = author;
    }


    public String getObjectId() {
        return objectId;
    }

    public String getText() {
        return text;
    }

    public String getCreated() {
        return created;
    }

    public BackendlessUser getAuthor() {
        return author;
    }

    public org.helpapaw.helpapaw.data.models.Comment getPojoComment(){
        return new org.helpapaw.helpapaw.data.models.Comment(objectId, "", "", created, text);
    }
}

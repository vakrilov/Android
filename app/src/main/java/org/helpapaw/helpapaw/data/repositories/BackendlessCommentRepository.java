package org.helpapaw.helpapaw.data.repositories;

import android.util.Log;

import com.backendless.Backendless;
import com.backendless.BackendlessCollection;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.exceptions.BackendlessFault;
import com.backendless.persistence.BackendlessDataQuery;
import com.backendless.persistence.QueryOptions;

import org.helpapaw.helpapaw.data.models.Comment;
import org.helpapaw.helpapaw.data.models.backendless.FINComment;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.helpapaw.helpapaw.data.models.Comment.COMMENT_TYPE_USER_COMMENT;


/**
 * Created by iliyan on 8/4/16
 */
public class BackendlessCommentRepository implements CommentRepository {
    private static final String DATE_TIME_FORMAT = "MM/dd/yyyy hh:mm:ss";
    private static final String NAME_FIELD = "name";
    private static final String CREATED_FIELD = "created";

    @Override
    public void getAllCommentsBySignalId(String signalId, final LoadCommentsCallback callback) {
        final List<Comment> comments = new ArrayList<>();

        String whereClause = "signalID = '" + signalId + "'";
        BackendlessDataQuery dataQuery = new BackendlessDataQuery();
        dataQuery.setWhereClause(whereClause);
        QueryOptions queryOptions = new QueryOptions();
        queryOptions.setPageSize(30);
        queryOptions.setSortBy(Collections.singletonList(CREATED_FIELD));
        dataQuery.setQueryOptions(queryOptions);

        Backendless.Persistence.of(FINComment.class).find(dataQuery, new AsyncCallback<BackendlessCollection<FINComment>>() {
                    @Override
                    public void handleResponse(BackendlessCollection<FINComment> foundComments) {
                        for (int i = 0; i < foundComments.getData().size(); i++) {
                            FINComment currentComment = foundComments.getData().get(i);
                            String authorName = null;
                            if (currentComment.getAuthor() != null) {
                                authorName = getToStringOrNull(currentComment.getAuthor().getProperty(NAME_FIELD));
                            }

                            Date dateCreated = null;
                            try {
                                String dateCreatedString = currentComment.getCreated();
                                DateFormat dateFormat = new SimpleDateFormat(DATE_TIME_FORMAT, Locale.getDefault());
                                dateCreated = dateFormat.parse(dateCreatedString);
                            }
                            catch (Exception ex) {
                                Log.d(BackendlessCommentRepository.class.getName(), "Failed to parse comment date.");
                            }

                            Comment comment = new Comment(currentComment.getObjectId(), authorName, dateCreated, currentComment.getText(), currentComment.getType());
                            comments.add(comment);
                        }

                        callback.onCommentsLoaded(comments);
                    }

                    @Override
                    public void handleFault(BackendlessFault fault) {
                        callback.onCommentsFailure(fault.getMessage());
                    }
                });
    }

    @Override
    public void saveComment(String commentText, String signalId, final SaveCommentCallback callback) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_TIME_FORMAT, Locale.getDefault());
        String currentDate = dateFormat.format(new Date());

        FINComment backendlessComment = new FINComment(commentText, currentDate, signalId, COMMENT_TYPE_USER_COMMENT, Backendless.UserService.CurrentUser());

        Backendless.Persistence.save(backendlessComment, new AsyncCallback<FINComment>() {
            public void handleResponse(FINComment newComment) {
                String authorName = null;
                if (newComment.getAuthor() != null) {
                    authorName = getToStringOrNull(newComment.getAuthor().getProperty(NAME_FIELD));
                }

                Date dateCreated = null;
                try {
                    String dateCreatedString = newComment.getCreated();
                    DateFormat dateFormat = new SimpleDateFormat(DATE_TIME_FORMAT, Locale.getDefault());
                    dateCreated = dateFormat.parse(dateCreatedString);
                }
                catch (Exception ex) {
                    Log.d(BackendlessCommentRepository.class.getName(), "Failed to parse comment date.");
                }

                Comment comment = new Comment(newComment.getObjectId(), authorName, dateCreated, newComment.getText(), COMMENT_TYPE_USER_COMMENT);
                callback.onCommentSaved(comment);
            }

            public void handleFault(BackendlessFault fault) {
                callback.onCommentFailure(fault.getMessage());
            }
        });
    }

    private String getToStringOrNull(Object object) {
        if (object != null) {
            return object.toString();
        } else {
            return null;
        }
    }
}

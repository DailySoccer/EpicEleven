package model;

/**
 * Created by gnufede on 12/05/14.
 */

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonView;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.bson.types.ObjectId;

public class XMLContest {
    public String name;
    public Date startDate;
    public String xml;

    @Override
    public int hashCode() {
        return new HashCodeBuilder(7, 61).append(startDate).append(xml).toHashCode();
    }

}
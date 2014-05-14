package model

/**
 * Created by gnufede on 12/05/14.
 */

import org.joda.time.DateTime
import com.fasterxml.jackson.annotation.JsonView
import org.apache.commons.lang3.builder.HashCodeBuilder
import org.bson.types.ObjectId

class XMLContest (var name: String, var startDate: DateTime, var headers: Map[String, String], var xml: String){
  override def hashCode: Int = {
    return new HashCodeBuilder(7, 61).append(startDate).append(xml).toHashCode
  }

}
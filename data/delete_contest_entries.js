db.contests.update({state: "ACTIVE"}, { $set : {'contestEntries': [] }} , {multi:true} )
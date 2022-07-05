package fb

case class LiveEvent(
                     id: Long
                    )

case class LiveEventsResponse(
                              result:  String,
                              request: String,
                              place:   String,
                              lang:    String,
                              events:  Seq[LiveEvent],
                              md5:     String
                             )

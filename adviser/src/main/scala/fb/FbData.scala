package fb

case class Cell(
                 isTitle:  Option[Boolean],
                 caption:  Option[String],
                 factorid: Option[Long],
                 eventid:  Option[Long],
                 value:    Option[Double]
               )

case class Row(
               isTitle: Option[Boolean],
               cells: Seq[Cell]
              )

case class Market(
                  marketId: Long,
                  ident: String,
                  sortOrder: Long,
                  caption: String,
                  //commonHeaders:,
                  rows: Seq[Row]
                )

case class OneScore(c1: String, c2:String, title: Option[String])

case class LiveEvent(
                      id: Long,
                      number: Long,
                      startTimeTimestamp: Long,
                      competitionId: Long,
                      competitionName: String,
                      competitionCaption: String,
                      skId: Long,
                      skName: String,
                      skSortOrder: Option[String],
                      regionId: Option[Long],
                      team1Id: Long,
                      team2Id: Long,
                      team1: String,
                      team2: String,
                      eventName: String,
                      name: String,
                      place: String,
                      priority: Long,
                      kind: Long,
                      rootKind: Long,
                      sortOrder: String,
                      //tv
                      sportViewId: Option[Long],
                      timer: Option[String],
                      timerSeconds: Option[Long],
                      timerDirection: Option[Long],
                      timerTimestampMsec: Option[Long],
                      scoreFunction: String,
                      //scoreComment:(0-1) https:\/\/elevensports.com\/ru\/view\/event\/cl4b8248c82l70jbfzgjhhcch,
                      //scoreCommentTail:https:\/\/elevensports.com\/ru\/view\/event\/cl4b8248c82l70jbfzgjhhcch,
                      scores: Seq[Seq[OneScore]],
                      markets: Seq[Market]
                    )

case class LiveEventsResponse(
                              result:  String,
                              request: String,
                              place:   String,
                              lang:    String,
                              events:  Seq[LiveEvent],
                              md5:     String
                             )

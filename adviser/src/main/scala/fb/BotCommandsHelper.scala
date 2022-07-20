package fb

object BotCommandsHelper {
  private val helpText :String =
    """
      |Commands:
      |/author - return contact information
      |/help
      |/res
    """.stripMargin

  def getHelpText = helpText

}

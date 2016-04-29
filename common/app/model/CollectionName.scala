package model


object CollectionName {
  // Identificador para cambiar entre Liga + Premier / Mundial / Eurocopa
  var SELECTOR_ID : String = ""

  // COLLECTIONS QUE SE CONSERVAN
  def SESSIONS: String = "sessions"
  def USERS: String = "users"
  def ORDERS: String = "orders"
  def REFUNDS: String = "refunds"
  def PROMOS: String = "promos"
  def BONUS: String = "bonus"
  def ACCOUNTING_TRANSACTIONS: String = "accountingTransactions"
  def PAYPAL_RESPONSES: String = "paypalResponses"
  def POINTS_TRANSLATION: String = "pointsTranslation"

  // COLLECTIONS QUE DEPENDEN DE COMPETITION
  def OPS_LOG: String = "opsLog".concat(SELECTOR_ID)
  def TEMPLATE_CONTESTS: String = "templateContests".concat(SELECTOR_ID)
  def TEMPLATE_MATCHEVENTS: String = "templateMatchEvents".concat(SELECTOR_ID)
  def TEMPLATE_SOCCERTEAMS: String = "templateSoccerTeams".concat(SELECTOR_ID)
  def TEMPLATE_SOCCERPLAYERS: String = "templateSoccerPlayers".concat(SELECTOR_ID)
  def CANCELLED_CONTESTENTRIES: String = "cancelledContestEntries".concat(SELECTOR_ID)
  def CONTESTS: String = "contests".concat(SELECTOR_ID)
  def STATS_SIMULATION: String = "statsSimulation".concat(SELECTOR_ID)
  def OPTA_COMPETITIONS: String = "optaCompetitions".concat(SELECTOR_ID)
  def OPTA_EVENTS: String = "optaEvents".concat(SELECTOR_ID)
  def OPTA_PLAYERS: String = "optaPlayers".concat(SELECTOR_ID)
  def OPTA_TEAMS: String = "optaTeams".concat(SELECTOR_ID)
  def OPTA_MATCHEVENTS: String = "optaMatchEvents".concat(SELECTOR_ID)
  def OPTA_MATCHEVENT_STATS: String = "optaMatchEventStats".concat(SELECTOR_ID)
  def JOBS: String = "jobs".concat(SELECTOR_ID)
  def NOTIFICATIONS: String = "notifications".concat(SELECTOR_ID)
  def OPTA_PROCESSOR: String = "optaProcessor".concat(SELECTOR_ID)
  def SIMULATOR: String = "simulator".concat(SELECTOR_ID)
}

# pint (github.com/cloudflare/pint) checks the label conventions of docs/alerting.md.

parser {
  exclude = [".*/tests/.*"]   # promtool unit tests are not rule files
}

# Every alert: severity and team decide where the notification goes.
rule {
  match {
    kind = "alerting"
  }
  label "severity" {
    severity = "bug"
    required = true
    value    = "(critical|warning|info|none)"
  }
  label "team" {
    severity = "bug"
    required = true
  }
}

# Every alert needs a summary: it is the line of the notification. SLO alerts get it from
# pyrra.dev/summary in observability/slo/*.yaml.
rule {
  match {
    kind = "alerting"
  }
  annotation "summary" {
    severity = "bug"
    required = true
  }
}

{{- define "spring-service.labels" -}}
app.kubernetes.io/name: {{ .Release.Name }}
app.kubernetes.io/part-of: java-blueprints
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}
{{- define "spring-service.selector" -}}
app.kubernetes.io/name: {{ .Release.Name }}
{{- end }}

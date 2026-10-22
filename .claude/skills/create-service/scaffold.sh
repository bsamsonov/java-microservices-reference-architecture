#!/usr/bin/env bash
# Mechanical part of create-service: copy a template service and rename everything that is
# service-level (artifact, root package, application class, database, ingress host).
# Module/feature-level names (orders: module "order", schema "orders", topic "orders.events";
# catalog: feature "product", schema "products") are left as is: they are replaced with the domain.
#
# Usage: scaffold.sh [--from orders|catalog] <name> [dest-root]
#   --from      template: orders (1, Modulith + domain model, default) or catalog (2, plain CRUD on Data JDBC)
#   <name>      kebab-case, with or without "-service": payments | loyalty-points-service
#   dest-root   where services/<name>-service is created (default: repo root); used for dry runs
set -euo pipefail

repo=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)

tpl=orders
if [[ ${1:-} == --from ]]; then tpl=${2:?--from needs orders|catalog}; shift 2; fi
[[ $tpl == orders || $tpl == catalog ]] || { echo "unknown template: $tpl (orders|catalog)" >&2; exit 1; }
src=$repo/services/$tpl-service
tcls=${tpl^}                                                 # Orders | Catalog

name=${1:?usage: scaffold.sh [--from orders|catalog] <name> [dest-root]}
name=${name%-service}
[[ $name =~ ^[a-z][a-z0-9]*(-[a-z0-9]+)*$ ]] || { echo "name must be kebab-case: $name" >&2; exit 1; }
[[ $name == "$tpl" ]] && { echo "$tpl is the template itself" >&2; exit 1; }

svc=$name-service                                            # loyalty-points-service
pkg=${name//-/}                                              # loyaltypoints
db=${name//-/_}                                              # loyalty_points
cls=$(sed -E 's/(^|-)([a-z])/\U\2/g' <<<"$name")             # LoyaltyPoints

dest=${2:-$repo}/services/$svc
[[ -e $dest ]] && { echo "already exists: $dest" >&2; exit 1; }
mkdir -p "$dest"

# tracked files only: no target/, no IDE files
git -C "$src" ls-files -z | tar -C "$src" --null -T - -cf - | tar -C "$dest" -xf -

base=src/main/java/io/github/bsamsonov
tbase=src/test/java/io/github/bsamsonov
mv "$dest/$base/$tpl" "$dest/$base/$pkg"
mv "$dest/$tbase/$tpl" "$dest/$tbase/$pkg"
mv "$dest/$base/$pkg/${tcls}Application.java" "$dest/$base/$pkg/${cls}Application.java"
mv "$dest/$tbase/$pkg/Test${tcls}Application.java" "$dest/$tbase/$pkg/Test${cls}Application.java"
mv "$dest/$tbase/$pkg/${tcls}FlowIntegrationTest.java" "$dest/$tbase/$pkg/${cls}FlowIntegrationTest.java"

# order of rules matters: specific before generic
grep -rlZ -e "$tpl" -e "$tcls" "$dest" --exclude-dir=.mvn --exclude=mvnw --exclude=mvnw.cmd | xargs -0 sed -i -E \
    -e "s/$tpl-service/$svc/g" \
    -e "s/io\.github\.bsamsonov\.$tpl/io.github.bsamsonov.$pkg/g" \
    -e "s/\bTest${tcls}Application\b/Test${cls}Application/g" \
    -e "s/\b${tcls}Application\b/${cls}Application/g" \
    -e "s/\b${tcls}FlowIntegrationTest\b/${cls}FlowIntegrationTest/g" \
    -e "s#(jdbc:postgresql://[^/]+/)$tpl#\1$db#g" \
    -e "s/^(    (username|password): )$tpl\$/\1$db/" \
    -e "s/secret: pg-$tpl,/secret: pg-$name,/g" \
    -e "s/host: $tpl\.localhost/host: $name.localhost/"

chmod +x "$dest/mvnw"

cat <<EOF
created $dest (template: $tpl-service)
  service  $svc
  package  io.github.bsamsonov.$pkg
  app      ${cls}Application
  database $db
still from the template (replace with the domain): see SKILL.md, step 2 (template 1) or 2c (template 2); README
EOF

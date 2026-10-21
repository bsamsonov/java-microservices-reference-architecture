# Inner dev loop on the k3d platform: `tilt up` (UI) or `tilt ci` (build, deploy, wait ready, exit).
# Requires `make -C platform up apps-namespace`.
allow_k8s_contexts('k3d-blueprints')
# Image registry is auto-detected from the k3d ConfigMap kube-public/local-registry-hosting.

def spring_service(name):
    path = 'services/' + name
    local_resource(name + '-jar', cmd='./mvnw -q -DskipTests package', dir=path,
                   deps=[path + '/src/main', path + '/pom.xml'], labels=[name])
    docker_build(name, path, only=['target/' + name + '-0.1.0-SNAPSHOT.jar', 'Dockerfile'])
    k8s_yaml(helm('charts/spring-service', name=name, namespace='apps',
                  values=[path + '/deploy/values.yaml']))
    k8s_resource(name, resource_deps=[name + '-jar'], labels=[name])

spring_service('orders-service')
spring_service('notifications-service')
spring_service('catalog-service')

# prepare autodeploy

kubectl -n raoa-dev create rolebinding jenkins-deploy --clusterrole=edit --serviceaccount=build:default
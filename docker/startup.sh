#!/usr/bin/env sh
# Script to perform any custom docker startup actions
# Allows local running where the jarfile is under ./build/lib
# or dockerfile running where the app jarfile is under /app
#
logmsg() {
    SCRIPTNAME=$(basename $0)
    echo "$SCRIPTNAME : $1"
}

logmsg "running and loading certificates ..."
if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME="/usr/local/openjdk-21"
fi
export KEYSTORE="$JAVA_HOME/lib/security/cacerts"
if [ -z "$CERTS_DIR" ]; then
    logmsg "Warning - expects \$CERTS_DIR to be set. i.e. export CERTS_DIR="/etc/certs
    logmsg "Defaulting to /etc/certs"
    export CERTS_DIR="/etc/certs"
fi

if [ ! -f "$KEYSTORE" ]; then
    logmsg "Error - expects keystore $KEYSTORE to already exist"
    exit 1
fi

export count=1
logmsg "Loading certificates from $CERTS_DIR into keystore $KEYSTORE"
for FILE in $(ls $CERTS_DIR)
do
    alias="mojcert$count"
    logmsg "Adding $CERTS_DIR/$FILE to keystore with alias $alias"
    keytool -importcert -file $CERTS_DIR/$FILE -keystore $KEYSTORE -storepass changeit -alias $alias -noprompt
    count=$((count+1))
done

keytool -list -keystore $KEYSTORE -storepass changeit | grep "Your keystore contains"

export LOCALJARFILE=$(ls ./build/libs/*.jar 2>/dev/null | grep -v 'plain' | head -n1)
export DOCKERJARFILE=$(ls /app/*.jar 2>/dev/null | grep -v 'plain' | head -n1)
if [ -f "$DOCKERJARFILE" ]; then
    logmsg "Running docker java jarfile $DOCKERJARFILE"
    java -jar $DOCKERJARFILE
elif [ -f "$LOCALJARFILE" ]; then
    logmsg "Running local java jarfile $LOCALJARFILE"
    java -jar $LOCALJARFILE
else
    logmsg "ERROR - No jarfile found. Unable to start application"
fi

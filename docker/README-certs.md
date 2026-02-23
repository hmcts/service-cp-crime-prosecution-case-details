# Some useful keytool commands
#
Ignore warning "Warning: use -cacerts option to access cacerts keystore"
Think the finger print for cpp-nonline is  A0:AF:DB:4F:...:CA:DA:14:C6
```
keytool -list -keystore $KEYSTORE -storepass changeit
keytool -list -keystore $KEYSTORE -storepass changeit | grep "A0:AF:DB"
keytool -delete -keystore $KEYSTORE -storepass changeit -alias localcert1
keytool -delete -keystore $KEYSTORE -storepass changeit -alias mykey
```


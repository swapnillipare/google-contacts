#!/bin/bash

#JAVA_HOME=/usr/java/jdk1.7.0_55
#JAR_FILE=/home/garyc/code/google-contacts/target/google-contact-editor.jar
propfile='properties.txt'
export LANG=en_US.UTF-8

operations='add del modify'

cd $HOME/code/google-contacts/workspace

#exit 1

accounts=`cat accounts.csv`
for account in $accounts
do
  user=`echo $account|cut -d, -f1`
  email=`echo $account|cut -d, -f2`
  pw=`echo $account|cut -d, -f3`
  filter=`echo $account|cut -d, -f4`
  #echo Converting LDIF to tab for $user with filter $filter
  /usr/sbin/slapcat -s "ou=Public,dc=pioneerind,dc=com" -a $filter | $HOME/bin/filterExtraFields.pl | $HOME/bin/ldif2tab.pl > $user.tab
  lc=`cat $user.tab|wc -l`
  if [ $lc -gt 1 ] ; then
    java -jar ../target/google-contact-editor.jar $user $email $pw $user.tab $propfile
    for operation in $operations
    do
      lc=`cat $user.$operation|wc -l`
      if [ $lc -gt 1 ] ; then
        $HOME/bin/tab2ldif.pl Public < $user.$operation | $HOME/bin/clean_ldif.pl Public > temp.ldif
        $HOME/bin/modify_ldap.sh $operation temp.ldif
      fi
    done
  else
    echo Error in exporting from LDAP, skipping sync
  fi
done

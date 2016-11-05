mvn -Dfile.encoding=UTF-8 -DskipTests -DcreateChecksum=true clean source:jar javadoc:jar repository:bundle-create install --batch-mode

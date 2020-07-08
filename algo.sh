./gradlew gds-intro-graph-algorithms-exercises:convertBrowserGuide
./gradlew gds-intro-graph-algorithms:convertOnlineHtml
cd modules/gds-intro-graph-algorithms-exercises/build/browser-guide
python -m SimpleHTTPServer 3000
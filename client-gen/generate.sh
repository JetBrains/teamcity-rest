#!/usr/bin/env bash
if [[ ! -f "swagger.json" ]]; then
    echo "swagger.json should be placed in same directory as this script ($(pwd))"
    exit 1
fi

swagger-codegen generate -l java -c swagger.config.json -i swagger.json -o .

# Fix weird generated stuff
grep '__callback' src -rl | xargs -n 1 sed -i '.bak' 's/__callback/callback/ g'
find . -name '*.bak' | xargs rm
grep '__apiClient' src -rl | xargs -n 1 sed -i '.bak' 's/__apiClient/apiClient/ g'
find . -name '*.bak' | xargs rm
grep 'model.ArrayList;' src -rl | xargs -n 1 sed -i '.bak' '/import com.jetbrains.teamcity.rest.client.model.ArrayList;/ d'
find . -name '*.bak' | xargs rm
find . -name 'File.java' | xargs -n 1 sed -i '.bak' '/import java.io.File;/ d'
find . -name '*.bak' | xargs rm
find . -name 'TestApiTest.java' | xargs rm

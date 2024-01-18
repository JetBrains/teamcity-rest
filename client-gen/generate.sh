#!/usr/bin/env bash
#
# Copyright 2000-2024 JetBrains s.r.o.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

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
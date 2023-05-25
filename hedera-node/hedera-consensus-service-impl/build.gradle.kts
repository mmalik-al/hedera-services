/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins { id("com.hedera.hashgraph.conventions") }

description = "Default Hedera Consensus Service Implementation"

dependencies {
  javaModuleDependencies {
    annotationProcessor(gav("dagger.compiler"))

    testImplementation(testFixtures(project(":hedera-node:node-app-service-mono")))
    testImplementation(testFixtures(project(":hedera-node:node-app-spi")))
    testImplementation(testFixtures(project(":hedera-node:node-config")))
    testImplementation(project(":hedera-node:node-app"))
    testImplementation(project(":hedera-node:node-app-service-consensus-impl"))
    testImplementation(project(":hedera-node:node-app-service-token"))
    testImplementation(gav("com.google.protobuf"))
    testImplementation(gav("com.hedera.hashgraph.protobuf.java.api"))
    testImplementation(gav("com.swirlds.common"))
    testImplementation(gav("org.assertj.core"))
    testImplementation(gav("org.junit.jupiter.api"))
    testImplementation(gav("org.mockito"))
    testImplementation(gav("org.mockito.junit.jupiter"))

  }
}

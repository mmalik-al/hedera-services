/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.service.file.impl;

import com.google.auto.service.AutoService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.spi.FacilityFacade;
import com.hedera.node.app.spi.service.ServiceFactory;
import com.hedera.node.app.spi.service.ServiceProvider;
import edu.umd.cs.findbugs.annotations.NonNull;

@AutoService(ServiceFactory.class)
public class FileServiceFactory implements ServiceFactory<FileService> {

    @NonNull
    @Override
    public Class<FileService> getServiceClass() {
        return FileService.class;
    }

    @NonNull
    @Override
    public FileService createService(final ServiceProvider serviceProvider, final FacilityFacade facilityFacade) {
        return new FileServiceImpl();
    }
}

/*
 * Copyright (C) 2020  Consiglio Nazionale delle Ricerche
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package it.cnr.si.service;

import it.cnr.si.spring.storage.StorageObject;
import it.cnr.si.spring.storage.StoreService;
import it.cnr.si.spring.storage.config.StoragePropertyNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Created by francesco on 23/06/17.
 */

@Service
public class PrintStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrintStorageService.class);
    private static final String SEPARATOR = "/";

    private final StoreService storeService;

    @Value("${print.storage.base-path:/sigla-print}")
    private String basePath;

    public PrintStorageService(StoreService storeService) {
        LOGGER.info("using {}", storeService.getClass().getSimpleName());
        this.storeService = storeService;
    }

    private String fullPath(String id) {
        String base = basePath.endsWith(SEPARATOR) ? basePath.substring(0, basePath.length() - 1) : basePath;
        return id.startsWith(SEPARATOR) ? base.concat(id) : base.concat(SEPARATOR).concat(id);
    }

    private static String contentType(String name) {
        return name.toLowerCase().endsWith(".pdf") ? "application/pdf" : "application/octet-stream";
    }

    public CompletableFuture<Void> write(String id, byte[] byteArray) {
        LOGGER.info("writing {} bytes to {}", byteArray.length, id);
        return CompletableFuture.runAsync(() -> {
            String path = fullPath(id);
            int index = path.lastIndexOf(SEPARATOR);
            String folder = path.substring(0, index);
            String name = path.substring(index + 1);
            StorageObject existing = storeService.getStorageObjectByPath(path);
            if (existing != null) {
                storeService.updateStream(existing.getKey(), new ByteArrayInputStream(byteArray), contentType(name));
            } else {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put(StoragePropertyNames.NAME.value(), name);
                metadata.put(StoragePropertyNames.OBJECT_TYPE_ID.value(), StoragePropertyNames.CMIS_DOCUMENT.value());
                storeService.storeSimpleDocument(new ByteArrayInputStream(byteArray), contentType(name), folder, metadata);
            }
        });
    }

    public boolean delete(String id) {
        LOGGER.info("deleting {}", id);
        StorageObject storageObject = storeService.getStorageObjectByPath(fullPath(id));
        Boolean deleted = storageObject != null && Boolean.TRUE.equals(storeService.delete(storageObject));
        LOGGER.info("{} deleted = {}", id, deleted);
        return deleted;
    }

    public InputStream get(String id) {
        LOGGER.info("get {}", id);
        StorageObject storageObject = storeService.getStorageObjectByPath(fullPath(id));
        if (storageObject == null) {
            throw new IllegalArgumentException("Cannot find stored file " + id);
        }
        return storeService.getResource(storageObject);
    }
}

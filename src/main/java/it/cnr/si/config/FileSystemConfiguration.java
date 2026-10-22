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

package it.cnr.si.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * JasperSource che legge i report da una cartella del filesystem (cnr.filesystem.path).
 * Alternativa a {@link GitHubConfiguration}: va configurata una sola delle due sorgenti.
 */
@Configuration
@ConditionalOnProperty({"cnr.filesystem.path"})
@EnableConfigurationProperties({FileSystemConfigurationProperties.class})
public class FileSystemConfiguration {
    @Autowired
    private FileSystemConfigurationProperties fileSystemConfigurationProperties;

    @Bean
    public JasperSource createFileSystemJasperSource() {
        final Path root = Paths.get(fileSystemConfigurationProperties.getPath())
                .toAbsolutePath().normalize();
        return new JasperSource() {
            @Override
            public String getContentAsString(String key) throws Exception {
                return new String(getContentAsBytes(key), StandardCharsets.UTF_8);
            }

            @Override
            public byte[] getContentAsBytes(String key) throws Exception {
                return Files.readAllBytes(resolve(key));
            }

            /**
             * Risolve la chiave rispetto alla cartella radice, impedendo di uscirne
             * (es. "../../etc/passwd").
             */
            private Path resolve(String key) throws FileNotFoundException {
                String relative = key.replace('\\', '/');
                while (relative.startsWith("/")) {
                    relative = relative.substring(1);
                }
                Path file = root.resolve(relative).normalize();
                if (!file.startsWith(root)) {
                    throw new SecurityException("Percorso fuori dalla cartella dei report: " + key);
                }
                if (!Files.isRegularFile(file)) {
                    throw new FileNotFoundException(file.toString());
                }
                return file;
            }
        };
    }
}

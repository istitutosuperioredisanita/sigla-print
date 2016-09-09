package it.cnr.si.web;

import it.cnr.si.dto.PrintRequest;
import it.cnr.si.service.PrintService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;

/**
 * Created by francesco on 09/09/16.
 */

@RestController
public class PrintResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrintResource.class);

    @Autowired
    private PrintService printService;

    @PostMapping("/api/v1/print")
    public ResponseEntity<byte[]> print(@RequestBody PrintRequest printRequest) {
        LOGGER.info("print request: {}", printRequest);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/pdf"));
        String fileName = printRequest.getName();
        headers.add("content-disposition", "inline;filename=" +
                fileName);

        ByteArrayOutputStream outputStream = printService.print(printRequest.getId());

        return new ResponseEntity<>(outputStream.toByteArray(),
                headers, HttpStatus.OK);

    }


}

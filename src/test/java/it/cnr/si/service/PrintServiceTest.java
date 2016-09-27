package it.cnr.si.service;

import it.cnr.si.domain.Foo;
import it.cnr.si.repository.FooRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;

/**
 * Created by francesco on 09/09/16.
 */

@RunWith(SpringRunner.class)
@SpringBootTest
public class PrintServiceTest {

    public static final String ID = "reports/logs/batchlog.jrxml";
    @Autowired
    private PrintService printService;

    @Autowired
    private FooRepository fooRepository;

    @Test
    public void print() throws Exception {

        fooRepository.save(new Foo("titolone"));
        ByteArrayOutputStream baos = printService.print("foo-1234");
        assertEquals(919, baos.size());
    }


    @Test
    public void testCache() {

        printService.jasperReport(ID);
        printService.jasperReport(ID);
        printService.jasperReport(ID);
        printService.evict(ID);
        printService.jasperReport(ID);
        printService.jasperReport(ID);
        printService.jasperReport(ID);

    }

}
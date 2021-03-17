package de.sist.gitlab;


import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Test;

import java.util.List;

public class DeserializationTest {

    @Test
    public void testDeserialization() throws Exception {
        //Just test that it runs...

        Jackson.OBJECT_MAPPER.readValue(DeserializationTest.class.getResource("/pipelines.json"), new TypeReference<List<PipelineTo>>() {
        });
    }


}
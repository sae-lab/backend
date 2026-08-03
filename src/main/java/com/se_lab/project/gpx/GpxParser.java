package com.se_lab.project.gpx;

import com.se_lab.project.dto.GpxPoint;
import org.springframework.stereotype.Component;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class GpxParser {

    public List<GpxPoint> parse(String gpxXml) {

        List<GpxPoint> points = new ArrayList<>();

        try {

            var factory = DocumentBuilderFactory.newInstance();
            var builder = factory.newDocumentBuilder();

            var document =
                    builder.parse(
                            new ByteArrayInputStream(
                                    gpxXml.getBytes(StandardCharsets.UTF_8)
                            )
                    );


            var nodes =
                    document.getElementsByTagName("trkpt");


            for (int i = 0; i < nodes.getLength(); i++) {

                var node = nodes.item(i);

                var attributes = node.getAttributes();

                double lat =
                        Double.parseDouble(
                                attributes.getNamedItem("lat")
                                        .getNodeValue()
                        );

                double lon =
                        Double.parseDouble(
                                attributes.getNamedItem("lon")
                                        .getNodeValue()
                        );


                double elevation = 0;


                var eleNodes =
                        ((org.w3c.dom.Element) node)
                                .getElementsByTagName("ele");

                if (eleNodes.getLength() > 0) {
                    elevation =
                            Double.parseDouble(
                                    eleNodes.item(0)
                                            .getTextContent()
                            );
                }


                points.add(
                        new GpxPoint(
                                lat,
                                lon,
                                elevation
                        )
                );
            }


        } catch (Exception e) {
            throw new RuntimeException("GPX 파싱 실패", e);
        }
        return points;
    }
}
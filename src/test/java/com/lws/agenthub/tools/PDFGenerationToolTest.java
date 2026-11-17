package com.lws.agenthub.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PDFGenerationToolTest {

    @Test
    void generatePDF() {
        PDFGenerationTool tool = new PDFGenerationTool();
        String fileName = "平台架构说明.pdf";
        String content = "AgentHub 平台架构说明 https://github.com/Se7en-exe";
        String result = tool.generatePDF(fileName, content);
        assertNotNull(result);
    }
}
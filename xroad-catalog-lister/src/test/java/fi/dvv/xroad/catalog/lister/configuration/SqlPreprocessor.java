/**
 * The MIT License
 *
 * Copyright (c) 2023- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2016-2023 Finnish Digital Agency
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package fi.dvv.xroad.catalog.lister.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SqlPreprocessor {

    private SqlPreprocessor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String preprocessSql(String pathToSqlTemplate) throws IOException {
        String template = Files.readString(Paths.get(pathToSqlTemplate));

        Pattern filePattern = Pattern.compile("@file\\('(.+?)'\\)");
        Matcher matcher = filePattern.matcher(template);

        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String filePath = matcher.group(1);
            String fileContent = Files.readString(Paths.get(filePath));

            String escapedContent = fileContent.replace("'", "''");
            matcher.appendReplacement(sb, "'" + escapedContent + "'");
        }

        matcher.appendTail(sb);
        return sb.toString();
    }
}

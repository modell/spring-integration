/*
 * Copyright 2014-2017 the original author or authors.
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

package org.springframework.integration.sftp.session;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.file.DefaultFileNameGenerator;
import org.springframework.integration.file.remote.ClientCallbackWithoutResult;
import org.springframework.integration.file.remote.SessionCallbackWithoutResult;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.SftpTestSupport;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

/**
 * @author Gary Russell
 * @since 4.1
 *
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext
public class SftpRemoteFileTemplateTests extends SftpTestSupport {

	@Autowired
	private CachingSessionFactory<LsEntry> sessionFactory;

	@Test
	public void testINT3412AppendStatRmdir() {
		SftpRemoteFileTemplate template = new SftpRemoteFileTemplate(sessionFactory);
		DefaultFileNameGenerator fileNameGenerator = new DefaultFileNameGenerator();
		fileNameGenerator.setExpression("'foobar.txt'");
		template.setFileNameGenerator(fileNameGenerator);
		template.setRemoteDirectoryExpression(new LiteralExpression("foo/"));
		template.setUseTemporaryFileName(false);
		template.execute(session -> {
			session.mkdir("foo/");
			return session.mkdir("foo/bar/");
		});
		template.append(new GenericMessage<String>("foo"));
		template.append(new GenericMessage<String>("bar"));
		assertTrue(template.exists("foo/foobar.txt"));
		template.executeWithClient((ClientCallbackWithoutResult<ChannelSftp>) client -> {
			try {
				SftpATTRS file = client.lstat("foo/foobar.txt");
				assertEquals(6, file.getSize());
			}
			catch (SftpException e) {
				throw new RuntimeException(e);
			}
		});
		template.execute((SessionCallbackWithoutResult<LsEntry>) session -> {
			LsEntry[] files = session.list("foo/");
			assertEquals(4, files.length);
			assertTrue(session.remove("foo/foobar.txt"));
			assertTrue(session.rmdir("foo/bar/"));
			files = session.list("foo/");
			assertEquals(2, files.length);
			List<LsEntry> list = Arrays.asList(files);
			assertThat(list.stream().map(l -> l.getFilename()).collect(Collectors.toList()),
					containsInAnyOrder(".", ".."));
			assertTrue(session.rmdir("foo/"));
		});
		assertFalse(template.exists("foo"));
	}

	@Configuration
	public static class Config {

		@Bean
		public SessionFactory<LsEntry> ftpSessionFactory() {
			return SftpRemoteFileTemplateTests.sessionFactory();
		}

	}

}

package com.solvd.qa.util;

import java.io.StringWriter;
import java.util.Properties;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.Version;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FreemarkerUtil {

	private final static String VERSION = "2.3.31";

	public static String processTemplate(String tmplPath, Properties p) {
		Configuration cfg = new Configuration(new Version(VERSION));

		cfg.setClassLoaderForTemplateLoading(FreemarkerUtil.class.getClassLoader(), "/");
		cfg.setDefaultEncoding("UTF-8");

		try {
			Template template = cfg.getTemplate(tmplPath);

			StringWriter out = new StringWriter();
			try {

				template.process(p, out);
				String res = out.getBuffer().toString();
				out.flush();

				return res;
			} finally {
				out.close();
			}
		} catch (Exception e) {
			log.error("Exception during template processing", e);
			return null;
		}
	}

	public static void main(String[] args) {
		processTemplate("views/init_job_view.json", new Properties());
	}

}

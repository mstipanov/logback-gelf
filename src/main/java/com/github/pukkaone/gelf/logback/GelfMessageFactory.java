package com.github.pukkaone.gelf.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.github.pukkaone.gelf.protocol.GelfMessage;

public interface GelfMessageFactory {

    GelfMessage createMessage(GelfAppender appender, ILoggingEvent event);
}

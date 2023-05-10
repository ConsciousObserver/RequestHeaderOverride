
This project provides a request wrapper that can be used to override value of some headers with their counter parts with a suffix. For example, HOST header value can be overridden in HOST-XXX header. It is useful in cases when load balancer doesn't preserve forwarded-* headers.

It also supports constant values for some headers.


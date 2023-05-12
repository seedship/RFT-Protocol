
The protocol-specification is written in the Internet-Draft style of the IETF
RFCs.  The specification is written in Markdown in [spec.md](spec.md)
and then compiled to XML using [mmark](https://github.com/mmarkdown/mmark/) and
from xml to text or html using [xml2rfc](https://xml2rfc.tools.ietf.org/).

To compile the document, make sure to install these dependencies and then use
the `compile-md` script:

```shell
# Install dependencies once
go get github.com/mmarkdown/mmark
pip install --user xml2rfc

# Happily compile the specification as often as you desire 
./compile-md spec.md
```

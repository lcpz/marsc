CPLEXDIR = /opt/ibm/ILOG/CPLEX_Studio201

IDIR = $(CPLEXDIR)/cplex/include
LDIR = $(CPLEXDIR)/cplex/lib/x86-64_linux/static_pic

CC = gcc
CFLAGS = -m64 -fPIC -I$(IDIR) -DIL_STD -L$(LDIR) -O3 -march=native -flto -Wall
LIBS =-lcplex -lm -lpthread -ldl

xmip:
	$(CC) xmip.c -o xmip $(CFLAGS) $(LIBS)

.PHONY: clean

clean:
	/bin/rm -fr xmip

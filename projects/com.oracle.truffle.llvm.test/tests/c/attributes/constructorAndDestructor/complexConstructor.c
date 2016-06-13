#include<stdio.h>
 

void myStartupFun (void) __attribute__ ((constructor));
 
void myCleanupFun (void) __attribute__ ((destructor));
 
void myStartupFun (void)
{
    printf ("startup\n");
}
 
void myCleanupFun (void)
{
    printf ("cleanup\n");
}
 
int main (void)
{
    printf ("In main\n");
    return 0;
}
